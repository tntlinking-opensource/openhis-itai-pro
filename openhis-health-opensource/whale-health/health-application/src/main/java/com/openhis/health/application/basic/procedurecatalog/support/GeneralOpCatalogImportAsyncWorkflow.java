/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.procedurecatalog.support;

import com.openhis.health.domain.clinical.proceduredefinition.command.GeneralOpImportRowCommand;
import com.openhis.health.domain.clinical.proceduredefinition.service.ProcedureDefinitionDomainService;
import com.openhis.health.infrastructure.integration.insurance.catalog.InsuranceSurgeryCatalogRow;
import com.openhis.health.infrastructure.integration.insurance.catalog.InsuranceSurgeryCatalogSourceExtension;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.infrastructure.multitenancy.TenantContext;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 医保「手术与操作」异步导入：从 yb 1308 镜像灌入 general_op。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeneralOpCatalogImportAsyncWorkflow {

    private final TenantContext tenantContext;
    private final InsuranceSurgeryCatalogSourceExtension surgeryCatalogSourceExtension;
    private final ProcedureDefinitionDomainService procedureDefinitionDomainService;

    private final ConcurrentHashMap<String, GeneralOpCatalogImportJob> jobs = new ConcurrentHashMap<>();

    private final ExecutorService importExecutor = Executors.newFixedThreadPool(1, r -> {
        Thread t = new Thread(r, "general-op-catalog-import");
        t.setDaemon(true);
        return t;
    });

    /**
     * 启动异步导入。
     *
     * @return jobId
     */
    public String startImport() {
        if (!surgeryCatalogSourceExtension.isEnabled()) {
            throw new ValidationException("从医保导入失败：未启用医保模块，请先启用 CHS 并下载 1308 手术操作目录");
        }
        String jobId = "GENERAL-OP-IMP-" + UUID.randomUUID().toString().replace("-", "");
        GeneralOpCatalogImportJob job = new GeneralOpCatalogImportJob();
        job.setJobId(jobId);
        job.setStatus(GeneralOpCatalogImportJob.STATUS_PENDING);
        job.setPercent(0);
        job.setMessage("排队中");
        job.setTenantId(TenantUtils.getCurrentTenantId());
        jobs.put(jobId, job);

        SecurityContext securityContext = SecurityContextHolder.getContext();
        importExecutor.execute(() -> this.runJob(jobId, securityContext));
        return jobId;
    }

    /**
     * 查询任务进度。
     *
     * @param jobId 任务 ID
     * @return 任务快照
     */
    public GeneralOpCatalogImportJob getJob(String jobId) {
        if (StringUtils.isEmpty(jobId)) {
            throw new BusinessException("GENERAL_OP_IMPORT_001", "查询导入进度失败：jobId 不能为空");
        }
        GeneralOpCatalogImportJob job = jobs.get(jobId.trim());
        if (job == null) {
            throw new BusinessException("GENERAL_OP_IMPORT_002", "查询导入进度失败：任务不存在或已过期");
        }
        return job;
    }

    private void runJob(String jobId, SecurityContext securityContext) {
        GeneralOpCatalogImportJob job = jobs.get(jobId);
        if (job == null) {
            return;
        }
        SecurityContextHolder.setContext(securityContext);
        if (job.getTenantId() != null) {
            tenantContext.setCurrentTenantId(job.getTenantId());
        }
        try {
            job.setStatus(GeneralOpCatalogImportJob.STATUS_RUNNING);
            job.setStage(GeneralOpCatalogImportJob.STAGE_LOADING);
            job.setPercent(5);
            job.setMessage("正在读取医保手术目录…");

            List<InsuranceSurgeryCatalogRow> sourceRows = surgeryCatalogSourceExtension.listActiveSurgeryRows();
            List<GeneralOpImportRowCommand> rows = toCommands(sourceRows);
            if (rows.isEmpty()) {
                throw new ValidationException("从医保导入失败：本地无 1308 手术操作数据，请先在医保目录下载中拉取手术操作目录");
            }
            job.setTotal(rows.size());
            job.setStage(GeneralOpCatalogImportJob.STAGE_WRITING);
            job.setPercent(10);
            job.setMessage("正在写入目录…");

            int success = procedureDefinitionDomainService.importGeneralOpCatalog(rows, processed -> {
                job.setProcessed(processed);
                int total = job.getTotal();
                if (total > 0) {
                    int percent = 10 + (int) Math.min(89, Math.floor(processed * 89.0 / total));
                    job.setPercent(percent);
                }
                job.setMessage("正在写入 " + processed + "/" + job.getTotal());
            });

            job.setSuccessCount(success);
            job.setProcessed(success);
            job.setStatus(GeneralOpCatalogImportJob.STATUS_SUCCEEDED);
            job.setStage(GeneralOpCatalogImportJob.STAGE_DONE);
            job.setPercent(100);
            job.setMessage("导入完成，共写入 " + success + " 条");
            log.info("医保手术与操作导入完成 jobId={}, success={}", jobId, success);
        } catch (Exception ex) {
            log.error("医保手术与操作导入失败 jobId={}", jobId, ex);
            job.setStatus(GeneralOpCatalogImportJob.STATUS_FAILED);
            job.setStage(GeneralOpCatalogImportJob.STAGE_DONE);
            job.setPercent(100);
            String msg = ex.getMessage();
            if (StringUtils.isEmpty(msg)) {
                msg = ex.getClass().getSimpleName();
            }
            job.setMessage("导入失败：" + msg);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private List<GeneralOpImportRowCommand> toCommands(List<InsuranceSurgeryCatalogRow> sourceRows) {
        List<GeneralOpImportRowCommand> rows = new ArrayList<>();
        if (sourceRows == null) {
            return rows;
        }
        for (InsuranceSurgeryCatalogRow source : sourceRows) {
            if (source == null) {
                continue;
            }
            String code = StringUtils.trimToNull(source.getHilistCode());
            String name = StringUtils.trimToNull(source.getHilistName());
            if (code == null || name == null
                || "null".equalsIgnoreCase(code)
                || "null".equalsIgnoreCase(name)) {
                continue;
            }
            GeneralOpImportRowCommand cmd = new GeneralOpImportRowCommand();
            cmd.setProcedureCode(code);
            cmd.setName(name);
            cmd.setPyStr(StringUtils.trimToNull(source.getPinyin()));
            rows.add(cmd);
        }
        return rows;
    }
}
