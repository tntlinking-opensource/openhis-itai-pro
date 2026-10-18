/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.support;

import com.zxplat.plugin.chs.application.notice.support.dto.NoticeApplyJob;
import com.zxplat.plugin.chs.domain.notice.command.ApplyNoticeDocCommand;
import com.zxplat.plugin.chs.domain.notice.model.ApplyNoticeDocResult;
import com.zxplat.plugin.chs.domain.notice.model.NoticeDocView;
import com.zxplat.plugin.chs.domain.notice.queryservice.CatalogNoticeDocQueryService;
import com.zxplat.plugin.chs.domain.notice.service.CatalogNoticeApplyDomainService;
import com.zxplat.plugin.chs.domain.notice.support.CatalogNoticeCanonicalSemantics;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantContext;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异步应用工作流：单文件内分批短事务；支持勾选多文件串行应用并暴露进度。
 */
@Component
@RequiredArgsConstructor
public class NoticeApplyWorkflow {

    private final CatalogNoticeApplyDomainService catalogNoticeApplyDomainService;
    private final CatalogNoticeDocQueryService catalogNoticeDocQueryService;
    private final TenantContext tenantContext;
    private final ConcurrentHashMap<String, NoticeApplyJob> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> runningFlags = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> runningDocJobs = new ConcurrentHashMap<>();
    private final ExecutorService applyExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "chs-notice-apply");
        t.setDaemon(true);
        return t;
    });

    /**
     * 启动单文件异步应用。
     *
     * @param docId      文件头
     * @param operatorId 操作人可空
     * @return jobId
     */
    public String startApply(Long docId, Long operatorId) {
        if (docId == null) {
            throw new BusinessException(ChsErrorCodes.NOTICE_DOC_NOT_FOUND, "docId 不能为空");
        }
        return startApplyBatch(List.of(docId), operatorId);
    }

    /**
     * 启动多文件串行异步应用（同租户；同 doc 禁止并行）。
     *
     * @param docIds     文件头列表（去重保序）
     * @param operatorId 操作人可空
     * @return jobId
     */
    public String startApplyBatch(List<Long> docIds, Long operatorId) {
        List<Long> targets = normalizeDocIds(docIds);
        if (targets.isEmpty()) {
            throw new BusinessException(ChsErrorCodes.NOTICE_DOC_NOT_FOUND, "docIds 不能为空");
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PARAM_INVALID, "当前未选择租户，无法应用");
        }

        int totalPending = 0;
        List<Long> applyable = new ArrayList<>();
        Map<Long, NoticeDocView> views = new HashMap<>();
        for (Long docId : targets) {
            NoticeDocView doc = catalogNoticeDocQueryService.getDocById(docId, tenantId);
            views.put(docId, doc);
            if (!CatalogNoticeCanonicalSemantics.hasBusinessDate(doc.getNoticeDate(), doc.getEffectiveDate())) {
                throw new BusinessException(
                    ChsErrorCodes.NOTICE_APPLY_DENIED,
                    "通知日与生效日均为空，禁止应用。请补洗数日期后重新入库，docId=" + docId);
            }
            String status = doc.getApplyStatus();
            if (status == null || status.isBlank()) {
                status = "NOT_APPLIED";
            }
            if ("REVOKED".equals(status)) {
                throw new BusinessException(
                    ChsErrorCodes.NOTICE_APPLY_DENIED,
                    "文件已撤销，禁止应用，docId=" + docId);
            }
            if ("APPLIED".equals(status)) {
                continue;
            }
            if (runningDocJobs.containsKey(docId)) {
                throw new BusinessException(
                    ChsErrorCodes.NOTICE_APPLY_DENIED,
                    "文件正在应用中，docId=" + docId);
            }
            // APPLYING：视为可续跑（进程中断残留）；NOT_APPLIED/PARTIAL 正常
            if (!"NOT_APPLIED".equals(status)
                && !"PARTIAL".equals(status)
                && !"APPLYING".equals(status)) {
                throw new BusinessException(
                    ChsErrorCodes.NOTICE_APPLY_DENIED,
                    "文件当前状态不可应用：" + status + "，docId=" + docId);
            }
            totalPending += catalogNoticeApplyDomainService.countPendingApplyLines(docId, tenantId);
            applyable.add(docId);
        }
        if (applyable.isEmpty()) {
            throw new BusinessException(
                ChsErrorCodes.NOTICE_APPLY_DENIED,
                "没有可应用的文件（已全部应用或无可续跑行）");
        }
        applyable.sort(byBusinessDateThenId(views));

        String jobId = UUID.randomUUID().toString().replace("-", "");
        NoticeApplyJob job = new NoticeApplyJob();
        job.setJobId(jobId);
        job.setStatus(NoticeApplyJob.STATUS_PENDING);
        job.setMessage("排队中");
        job.setTenantId(tenantId);
        job.setOperatorId(operatorId);
        job.setDocId(applyable.get(0));
        job.getRemainingDocIds().addAll(applyable);
        job.setDocTotal(applyable.size());
        job.setDocProcessed(0);
        job.setTotal(Math.max(totalPending, 1));
        job.setProcessed(0);
        jobs.put(jobId, job);
        for (Long docId : applyable) {
            runningDocJobs.put(docId, jobId);
        }
        submitRun(jobId);
        return jobId;
    }

    /**
     * 取进度快照。
     *
     * @param jobId 任务 ID
     * @return 任务
     */
    public NoticeApplyJob getJob(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PARAM_INVALID, "jobId 不能为空");
        }
        NoticeApplyJob job = jobs.get(jobId.trim());
        if (job == null) {
            throw new BusinessException(
                ChsErrorCodes.NOTICE_PREVIEW_EXPIRED,
                "应用任务不存在或已过期；请重新发起应用");
        }
        return job;
    }

    private void submitRun(String jobId) {
        AtomicBoolean flag = runningFlags.computeIfAbsent(jobId, k -> new AtomicBoolean(false));
        if (!flag.compareAndSet(false, true)) {
            return;
        }
        applyExecutor.execute(() -> {
            NoticeApplyJob job = jobs.get(jobId);
            if (job == null) {
                flag.set(false);
                return;
            }
            tenantContext.setCurrentTenantId(job.getTenantId());
            try {
                job.setStatus(NoticeApplyJob.STATUS_RUNNING);
                job.setMessage("应用中");
                runRemaining(job);
                if (job.getRemainingDocIds().isEmpty()) {
                    job.setCurrentDocId(null);
                    job.setProcessed(job.getTotal());
                    job.setDocProcessed(job.getDocTotal());
                    job.setStatus(NoticeApplyJob.STATUS_SUCCEEDED);
                    job.setMessage("应用完成");
                } else {
                    job.setStatus(NoticeApplyJob.STATUS_FAILED);
                    if (job.getMessage() == null || job.getMessage().isBlank()) {
                        job.setMessage("应用中断，剩余 " + job.getRemainingDocIds().size() + " 个文件未完成");
                    }
                }
            } catch (BusinessException ex) {
                job.setStatus(NoticeApplyJob.STATUS_FAILED);
                job.setMessage(ex.getMessage());
            } catch (Exception ex) {
                job.setStatus(NoticeApplyJob.STATUS_FAILED);
                job.setMessage("应用失败：" + ex.getMessage());
            } finally {
                releaseDocLocks(job);
                tenantContext.clear();
                flag.set(false);
            }
        });
    }

    private void runRemaining(NoticeApplyJob job) {
        int progressBase = job.getProcessed();
        while (!job.getRemainingDocIds().isEmpty()) {
            Long docId = job.getRemainingDocIds().get(0);
            job.setDocId(docId);
            job.setCurrentDocId(docId);
            int pending = catalogNoticeApplyDomainService.countPendingApplyLines(docId, job.getTenantId());
            job.setMessage("应用文件 " + (job.getDocProcessed() + 1) + "/" + job.getDocTotal()
                + "（docId=" + docId + "）");

            ApplyNoticeDocCommand command = new ApplyNoticeDocCommand();
            command.setTenantId(job.getTenantId());
            command.setDocId(docId);
            command.setOperatorId(job.getOperatorId());
            command.setProgressBase(progressBase);
            command.setProgressTotalOverride(job.getTotal());
            command.setProgressCallback((processed, total) -> {
                job.setProcessed(processed);
                if (total > 0) {
                    job.setTotal(total);
                }
                job.setMessage("文件 " + (job.getDocProcessed() + 1) + "/" + job.getDocTotal()
                    + "：已处理 " + processed + "/" + job.getTotal() + " 行");
            });
            ApplyNoticeDocResult result = catalogNoticeApplyDomainService.applyDoc(command);
            job.addFromResult(result);
            progressBase += pending;
            job.setProcessed(progressBase);
            job.getRemainingDocIds().remove(0);
            job.setDocProcessed(job.getDocProcessed() + 1);
            runningDocJobs.remove(docId, job.getJobId());
        }
    }

    private void releaseDocLocks(NoticeApplyJob job) {
        if (job == null) {
            return;
        }
        if (job.getRemainingDocIds() != null) {
            for (Long docId : job.getRemainingDocIds()) {
                runningDocJobs.remove(docId, job.getJobId());
            }
        }
        if (job.getCurrentDocId() != null) {
            runningDocJobs.remove(job.getCurrentDocId(), job.getJobId());
        }
        if (job.getDocId() != null) {
            runningDocJobs.remove(job.getDocId(), job.getJobId());
        }
    }

    private static List<Long> normalizeDocIds(List<Long> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            return List.of();
        }
        Set<Long> seen = new LinkedHashSet<>();
        for (Long id : docIds) {
            if (id != null) {
                seen.add(id);
            }
        }
        return new ArrayList<>(seen);
    }

    /**
     * 批量应用按通知日、生效日、主键升序，避免勾选顺序打乱局文时序。
     *
     * @param views 已加载的文件头
     * @return 比较器
     */
    private static Comparator<Long> byBusinessDateThenId(Map<Long, NoticeDocView> views) {
        return Comparator
            .comparing((Long id) -> businessDate(views.get(id)), Comparator.nullsLast(LocalDate::compareTo))
            .thenComparing(id -> {
                NoticeDocView view = views.get(id);
                if (view == null) {
                    return id;
                }
                if (view.getId() == null) {
                    return id;
                }
                return view.getId();
            });
    }

    /**
     * 排序用业务日：通知日优先，否则生效日。
     *
     * @param view 文件头
     * @return 日期或 null
     */
    private static LocalDate businessDate(NoticeDocView view) {
        if (view == null) {
            return null;
        }
        if (view.getNoticeDate() != null) {
            return view.getNoticeDate();
        }
        return view.getEffectiveDate();
    }
}

