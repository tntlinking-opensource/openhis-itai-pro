/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.procedurecatalog.support;

import com.zxplat.domain.clinical.proceduredefinition.command.ClinicalProcV3ImportRowCommand;
import com.zxplat.domain.clinical.proceduredefinition.service.ProcedureDefinitionDomainService;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantContext;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 国家临床版手术 3.0 异步导入：立即返回 jobId，后台解析并分批写入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicalProcV3CatalogImportAsyncWorkflow {

    private static final int MAX_FILE_BYTES = 40 * 1024 * 1024;

    private final TenantContext tenantContext;
    private final ClinicalProcV3CatalogWorkbookParser workbookParser;
    private final ProcedureDefinitionDomainService procedureDefinitionDomainService;

    private final ConcurrentHashMap<String, ClinicalProcV3CatalogImportJob> jobs = new ConcurrentHashMap<>();

    private final ExecutorService importExecutor = Executors.newFixedThreadPool(1, r -> {
        Thread t = new Thread(r, "clinical-proc-v3-import");
        t.setDaemon(true);
        return t;
    });

    /**
     * 启动异步导入。
     *
     * @param fileBytes 文件字节
     * @param fileName  文件名
     * @return jobId
     */
    public String startImport(byte[] fileBytes, String fileName) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new ValidationException("导入国家临床版手术3.0失败：文件不能为空");
        }
        if (fileBytes.length > MAX_FILE_BYTES) {
            throw new ValidationException("导入国家临床版手术3.0失败：文件过大（超过 40MB）");
        }
        String jobId = "CLINICAL-PROC-V3-IMP-" + UUID.randomUUID().toString().replace("-", "");
        ClinicalProcV3CatalogImportJob job = new ClinicalProcV3CatalogImportJob();
        job.setJobId(jobId);
        job.setStatus(ClinicalProcV3CatalogImportJob.STATUS_PENDING);
        job.setPercent(0);
        job.setMessage("排队中");
        job.setFileBytes(fileBytes);
        job.setFileName(fileName);
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
    public ClinicalProcV3CatalogImportJob getJob(String jobId) {
        if (StringUtils.isEmpty(jobId)) {
            throw new BusinessException("CLINICAL_PROC_V3_IMPORT_001", "查询导入进度失败：jobId 不能为空");
        }
        ClinicalProcV3CatalogImportJob job = jobs.get(jobId.trim());
        if (job == null) {
            throw new BusinessException("CLINICAL_PROC_V3_IMPORT_002", "查询导入进度失败：任务不存在或已过期");
        }
        return job;
    }

    private void runJob(String jobId, SecurityContext securityContext) {
        ClinicalProcV3CatalogImportJob job = jobs.get(jobId);
        if (job == null) {
            return;
        }
        SecurityContextHolder.setContext(securityContext);
        if (job.getTenantId() != null) {
            tenantContext.setCurrentTenantId(job.getTenantId());
        }
        try {
            job.setStatus(ClinicalProcV3CatalogImportJob.STATUS_RUNNING);
            job.setStage(ClinicalProcV3CatalogImportJob.STAGE_PARSING);
            job.setPercent(5);
            job.setMessage("正在解析 Excel…");

            List<ClinicalProcV3ImportRowCommand> rows = workbookParser.parse(job.getFileBytes());
            job.setTotal(rows.size());
            job.setStage(ClinicalProcV3CatalogImportJob.STAGE_WRITING);
            job.setPercent(10);
            job.setMessage("正在写入目录…");

            int success = procedureDefinitionDomainService.importClinicalProcV3Catalog(rows, processed -> {
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
            job.setStatus(ClinicalProcV3CatalogImportJob.STATUS_SUCCEEDED);
            job.setStage(ClinicalProcV3CatalogImportJob.STAGE_DONE);
            job.setPercent(100);
            job.setMessage("导入完成，共写入 " + success + " 条");
            log.info("国临手术3.0导入完成 jobId={}, success={}", jobId, success);
        } catch (Exception ex) {
            log.error("国临手术3.0导入失败 jobId={}", jobId, ex);
            job.setStatus(ClinicalProcV3CatalogImportJob.STATUS_FAILED);
            job.setStage(ClinicalProcV3CatalogImportJob.STAGE_DONE);
            job.setPercent(100);
            String msg = ex.getMessage();
            if (StringUtils.isEmpty(msg)) {
                msg = ex.getClass().getSimpleName();
            }
            job.setMessage("导入失败：" + msg);
        } finally {
            job.setFileBytes(null);
            SecurityContextHolder.clearContext();
        }
    }
}
