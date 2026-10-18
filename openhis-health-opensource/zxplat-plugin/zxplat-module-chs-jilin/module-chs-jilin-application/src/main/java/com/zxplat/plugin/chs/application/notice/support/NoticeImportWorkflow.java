/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.support;

import com.zxplat.plugin.chs.application.notice.dto.NoticeScanImportResultDto;
import com.zxplat.plugin.chs.application.notice.support.dto.NoticeImportJob;
import com.zxplat.plugin.chs.application.notice.support.dto.NoticePreviewContext;
import com.zxplat.plugin.chs.application.notice.support.dto.NoticeScanCachedItem;
import com.zxplat.plugin.chs.application.notice.support.dto.NoticeScanSession;
import com.zxplat.plugin.chs.domain.notice.command.PersistNoticeDocCommand;
import com.zxplat.plugin.chs.domain.notice.model.NoticeDocView;
import com.zxplat.plugin.chs.domain.notice.service.CatalogNoticeImportDomainService;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantContext;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 确认入库异步工作流：逐包落库、进度可轮询；失败或进程中断后可对剩余包续跑。
 * <p>已成功入库的包靠 hash 防重，续跑不会双插。</p>
 */
@Component
@RequiredArgsConstructor
public class NoticeImportWorkflow {

    private static final String SCAN_NEW = "NEW";
    private static final String SCAN_DUPLICATE = "DUPLICATE";
    private static final String SCAN_FAILED = "FAILED";
    private static final String RESULT_IMPORTED = "IMPORTED";
    private static final String RESULT_SKIPPED = "SKIPPED";
    private static final String RESULT_FAILED = "FAILED";

    private final NoticeScanWorkflow noticeScanWorkflow;
    private final CatalogNoticeImportDomainService catalogNoticeImportDomainService;
    private final TenantContext tenantContext;
    private final ConcurrentHashMap<String, NoticeImportJob> importJobs = new ConcurrentHashMap<>();
    /** jobId → 是否有执行线程在跑，避免重复提交。 */
    private final ConcurrentHashMap<String, AtomicBoolean> runningFlags = new ConcurrentHashMap<>();
    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "chs-notice-import");
        t.setDaemon(true);
        return t;
    });

    /**
     * 启动异步入库；校验扫描会话后返回 jobId。
     *
     * @param scanToken  扫描令牌
     * @param packDirs   勾选；空=全部 NEW
     * @param contractId 契约
     * @param force      是否强制重导
     * @return jobId
     */
    public String startImport(String scanToken, List<String> packDirs, Long contractId, boolean force) {
        NoticeScanSession session = noticeScanWorkflow.loadScan(scanToken);
        if (packDirs != null) {
            for (String packDir : packDirs) {
                if (packDir == null || packDir.isBlank()) {
                    continue;
                }
                if (session.getItemsByPackDir().get(packDir.trim()) == null) {
                    throw new BusinessException(
                        ChsErrorCodes.NOTICE_PARAM_INVALID,
                        "扫描结果中无此子目录：" + packDir.trim() + "，请重新扫描后再入库");
                }
            }
        }
        List<NoticeScanCachedItem> targets = noticeScanWorkflow.resolveImportTargets(
            session, packDirs, force);
        if (targets.isEmpty()) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PARAM_INVALID, "没有可入库的标准包（请勾选 NEW 或重新扫描）");
        }

        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PARAM_INVALID, "当前未选择租户，无法入库");
        }
        Long resolvedContract = contractId;
        if (resolvedContract == null) {
            resolvedContract = session.getContractId();
        }

        String jobId = UUID.randomUUID().toString().replace("-", "");
        NoticeImportJob job = new NoticeImportJob();
        job.setJobId(jobId);
        job.setStatus(NoticeImportJob.STATUS_PENDING);
        job.setMessage("排队中");
        job.setScanToken(scanToken);
        job.setScanRoot(session.getScanRoot());
        job.setContractId(resolvedContract);
        job.setForce(force);
        job.setTenantId(tenantId);
        job.setProcessed(0);
        for (NoticeScanCachedItem item : targets) {
            if (item.getPackDir() != null && !item.getPackDir().isBlank()) {
                job.getRemainingPackDirs().add(item.getPackDir());
            }
        }
        if (job.getRemainingPackDirs().isEmpty()) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PARAM_INVALID, "待入库项缺少子目录名，请重新扫描");
        }
        job.setTotal(job.getRemainingPackDirs().size());
        importJobs.put(jobId, job);
        submitRun(jobId);
        return jobId;
    }

    /**
     * 续跑：任务未结束且仍有剩余包时重新提交执行线程。
     * <p>RUNNING 中则直接返回；SUCCEEDED 无需续跑。</p>
     *
     * @param jobId 任务 ID
     * @return 同一 jobId
     */
    public String resumeImport(String jobId) {
        NoticeImportJob job = requireJob(jobId);
        if (NoticeImportJob.STATUS_SUCCEEDED.equals(job.getStatus())) {
            return jobId;
        }
        if (job.remainingCount() <= 0) {
            finalizeJobStatus(job);
            return jobId;
        }
        noticeScanWorkflow.loadScan(job.getScanToken());
        if (NoticeImportJob.STATUS_RUNNING.equals(job.getStatus()) && isRunning(jobId)) {
            return jobId;
        }
        job.setStatus(NoticeImportJob.STATUS_PENDING);
        job.setMessage("续跑排队中");
        submitRun(jobId);
        return jobId;
    }

    /**
     * 取任务快照。
     *
     * @param jobId 任务 ID
     * @return 任务
     */
    public NoticeImportJob getImportJob(String jobId) {
        return requireJob(jobId);
    }

    private void submitRun(String jobId) {
        AtomicBoolean flag = runningFlags.computeIfAbsent(jobId, k -> new AtomicBoolean(false));
        if (!flag.compareAndSet(false, true)) {
            return;
        }
        importExecutor.execute(() -> {
            NoticeImportJob job = importJobs.get(jobId);
            if (job == null) {
                flag.set(false);
                return;
            }
            tenantContext.setCurrentTenantId(job.getTenantId());
            try {
                job.setStatus(NoticeImportJob.STATUS_RUNNING);
                job.setMessage("入库中");
                runRemaining(job);
                if (job.remainingCount() <= 0) {
                    job.setCurrentPackDir(null);
                    job.setProcessed(job.getTotal());
                    finalizeJobStatus(job);
                } else {
                    job.setStatus(NoticeImportJob.STATUS_FAILED);
                    if (job.getMessage() == null || job.getMessage().isBlank()) {
                        job.setMessage("入库中断，可继续剩余 " + job.remainingCount() + " 个包");
                    }
                }
            } catch (BusinessException ex) {
                job.setStatus(NoticeImportJob.STATUS_FAILED);
                job.setMessage(ex.getMessage());
            } catch (Exception ex) {
                job.setStatus(NoticeImportJob.STATUS_FAILED);
                job.setMessage("入库中断：" + ex.getMessage() + "（可继续剩余 "
                    + job.remainingCount() + " 个包）");
            } finally {
                tenantContext.clear();
                flag.set(false);
            }
        });
    }

    /**
     * 全部队列处理完后：有成功/跳过才算 SUCCEEDED；全失败则为 FAILED。
     */
    private void finalizeJobStatus(NoticeImportJob job) {
        int ok = job.getImportedCount() + job.getSkippedCount();
        if (ok == 0 && job.getFailedCount() > 0) {
            job.setStatus(NoticeImportJob.STATUS_FAILED);
            String detail = firstFailedMessage(job);
            if (detail == null || detail.isBlank()) {
                detail = "无成功入库";
            }
            job.setMessage("入库全部失败：" + detail);
            return;
        }
        job.setStatus(NoticeImportJob.STATUS_SUCCEEDED);
        if (job.getFailedCount() > 0) {
            job.setMessage("入库完成（成功 "
                + job.getImportedCount() + "，跳过 "
                + job.getSkippedCount() + "，失败 "
                + job.getFailedCount() + "）");
        } else {
            job.setMessage("入库完成");
        }
    }

    private String firstFailedMessage(NoticeImportJob job) {
        for (NoticeScanImportResultDto row : job.getResults()) {
            if (RESULT_FAILED.equals(row.getStatus()) && row.getMessage() != null && !row.getMessage().isBlank()) {
                return row.getMessage();
            }
        }
        return null;
    }

    private void runRemaining(NoticeImportJob job) {
        NoticeScanSession session = noticeScanWorkflow.loadScan(job.getScanToken());
        while (!job.getRemainingPackDirs().isEmpty()) {
            String packDir = job.getRemainingPackDirs().get(0);
            job.setCurrentPackDir(packDir);
            NoticeScanCachedItem item = session.getItemsByPackDir().get(packDir);
            if (item == null) {
                NoticeScanCachedItem missing = new NoticeScanCachedItem();
                missing.setPackDir(packDir);
                missing.setStatus(SCAN_FAILED);
                missing.setMessage("扫描结果中无此子目录，请重新扫描后重试");
                item = missing;
            }
            int hintLines = 0;
            if (item.getPreview() != null) {
                hintLines = item.getPreview().getLineCount();
            }
            if (hintLines > 1000) {
                job.setMessage("大包入库中（约 " + hintLines + " 行）：" + packDir);
            } else {
                job.setMessage("入库中：" + packDir);
            }
            NoticeScanImportResultDto row = importOneScanItem(
                item, job.getContractId(), job.isForce(), job.getScanRoot(), job.getTenantId());
            job.getResults().add(row);
            if (RESULT_IMPORTED.equals(row.getStatus())) {
                job.setImportedCount(job.getImportedCount() + 1);
                if (item.getStatus() != null) {
                    item.setStatus(SCAN_DUPLICATE);
                    item.setExistingDocId(row.getDocId());
                    item.setMessage(row.getMessage());
                }
            } else if (RESULT_SKIPPED.equals(row.getStatus())) {
                job.setSkippedCount(job.getSkippedCount() + 1);
                if (SCAN_NEW.equals(item.getStatus())) {
                    item.setStatus(SCAN_DUPLICATE);
                    item.setExistingDocId(row.getDocId());
                    item.setMessage(row.getMessage());
                }
            } else {
                job.setFailedCount(job.getFailedCount() + 1);
            }
            job.getRemainingPackDirs().remove(0);
            job.setProcessed(job.getTotal() - job.remainingCount());
            job.setMessage("已处理 " + job.getProcessed() + "/" + job.getTotal());
        }
    }

    /**
     * 同步逐包入库（供同步 IF 复用）。
     *
     * @param item       扫描缓存项
     * @param contractId 契约
     * @param force      强制重导
     * @param scanRoot   扫描根
     * @param tenantId   租户（异步线程必须显式传入）
     * @return 单包结果
     */
    public NoticeScanImportResultDto importOneScanItem(
            NoticeScanCachedItem item,
            Long contractId,
            boolean force,
            String scanRoot,
            Long tenantId) {
        NoticeScanImportResultDto row = new NoticeScanImportResultDto();
        row.setPackDir(item.getPackDir());

        if (SCAN_FAILED.equals(item.getStatus())) {
            row.setStatus(RESULT_FAILED);
            row.setMessage(item.getMessage());
            return row;
        }
        if (SCAN_DUPLICATE.equals(item.getStatus()) && !force) {
            row.setStatus(RESULT_SKIPPED);
            String msg = item.getMessage();
            if (msg == null || msg.isBlank()) {
                msg = "内容已入库，未勾选强制重导";
            }
            row.setMessage(msg);
            row.setDocId(item.getExistingDocId());
            return row;
        }
        if (!SCAN_NEW.equals(item.getStatus()) && !SCAN_DUPLICATE.equals(item.getStatus())) {
            row.setStatus(RESULT_SKIPPED);
            row.setMessage("状态不可入库：" + item.getStatus());
            return row;
        }
        NoticePreviewContext preview = item.getPreview();
        if (preview == null) {
            row.setStatus(RESULT_FAILED);
            row.setMessage("缺少解析上下文，请重新扫描");
            return row;
        }

        try {
            int cachedLines = 0;
            if (preview.getLines() != null) {
                cachedLines = preview.getLines().size();
            }
            if (preview.getPackBytes() == null || preview.getPackBytes().length == 0
                || preview.getLineCount() > cachedLines) {
                preview = noticeScanWorkflow.rematerializePack(scanRoot, item.getPackDir());
            }
            PersistNoticeDocCommand command = buildPersistCommand(preview, contractId, force, tenantId);
            NoticeDocView saved = catalogNoticeImportDomainService.persistImportedDoc(command);
            row.setStatus(RESULT_IMPORTED);
            row.setDocId(saved.getId());
            row.setMessage("入库成功，docId=" + saved.getId());
            return row;
        } catch (BusinessException ex) {
            if (ChsErrorCodes.NOTICE_DUPLICATE_HASH.equals(ex.getErrorCode())) {
                row.setStatus(RESULT_SKIPPED);
                row.setMessage(ex.getMessage());
                return row;
            }
            row.setStatus(RESULT_FAILED);
            row.setMessage(ex.getMessage());
            return row;
        } catch (RuntimeException ex) {
            row.setStatus(RESULT_FAILED);
            String detail = ex.getMessage();
            if (detail == null || detail.isBlank()) {
                detail = ex.getClass().getSimpleName();
            }
            Throwable cause = ex.getCause();
            if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
                detail = detail + "；cause=" + cause.getMessage();
            }
            row.setMessage("入库失败：" + detail);
            return row;
        }
    }

    private PersistNoticeDocCommand buildPersistCommand(
            NoticePreviewContext preview,
            Long contractId,
            boolean force,
            Long tenantId) {
        PersistNoticeDocCommand command = new PersistNoticeDocCommand();
        Long resolvedTenant = tenantId;
        if (resolvedTenant == null) {
            resolvedTenant = TenantUtils.getCurrentTenantId();
        }
        if (resolvedTenant == null) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PARSE_FAILED, "租户不能为空");
        }
        command.setTenantId(resolvedTenant);
        command.setContractId(contractId);
        command.setDocKind(preview.getDocKind());
        command.setCategory(preview.getCategory());
        command.setTitle(preview.getTitle());
        command.setSourceFilename(preview.getSourceFilename());
        command.setSourceFileHash(preview.getSourceFileHash());
        command.setPackBytes(preview.getPackBytes());
        command.setPackFileName(preview.getSourceFileHash() + ".zip");
        command.setNoticeDate(preview.getNoticeDate());
        command.setEffectiveDate(preview.getEffectiveDate());
        command.setSheetSummaryJson(preview.getSheetSummaryJson());
        command.setParseStatus(preview.getParseStatus());
        command.setForce(force);
        command.setLines(preview.getLines());
        return command;
    }

    private NoticeImportJob requireJob(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PARAM_INVALID, "jobId 不能为空");
        }
        NoticeImportJob job = importJobs.get(jobId.trim());
        if (job == null) {
            throw new BusinessException(
                ChsErrorCodes.NOTICE_PREVIEW_EXPIRED,
                "入库任务不存在或已过期；请重新扫描后继续（已入库包不会重复插入）");
        }
        return job;
    }

    private boolean isRunning(String jobId) {
        AtomicBoolean flag = runningFlags.get(jobId);
        return flag != null && flag.get();
    }
}
