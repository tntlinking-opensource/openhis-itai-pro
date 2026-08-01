/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.devicecatalog.support;

import com.openhis.health.application.basic.devicecatalog.dto.ImportDeviceCatalogErrorItemDto;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.share.constants.DeviceCatalogImportConstants;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 耗材目录异步导入工作流：启动任务、后台执行、进度轮询。
 *
 * <p>租户与 SecurityContext 在启动线程捕获，异步线程还原；机构主键直接写入行请求。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCatalogImportAsyncWorkflow {

    private static final int ERROR_MESSAGE_LIMIT = 20;

    private final TenantContext tenantContext;
    private final DeviceCatalogImportWorkbookParser workbookParser;
    private final DeviceCatalogImportRowValidator rowValidator;
    private final DeviceCatalogImportTerminologyIndex terminologyIndex;
    private final DeviceCatalogImportConceptEnsureService conceptEnsureService;
    private final DeviceCatalogImportErrorFileService errorFileService;
    private final DeviceCatalogImportWriteSupport writeSupport;
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;

    private final ConcurrentHashMap<String, DeviceCatalogImportJob> jobs = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, DeviceCatalogImportWriteSupport.ImportRowCreator> creators =
        new ConcurrentHashMap<>();

    private final ExecutorService importExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "dev-catalog-import");
        t.setDaemon(true);
        return t;
    });

    /**
     * 启动异步导入：立即返回 jobId，后台解析/校验/整批写入。
     *
     * @param fileBytes  xlsx 字节
     * @param fileName   原始文件名
     * @param orgId      会话机构
     * @param operatorId 操作人
     * @param creator    单行创建回调（通常 AppService::createOneForImport）
     * @return jobId
     */
    public String startImport(
        byte[] fileBytes,
        String fileName,
        Long orgId,
        Long operatorId,
        DeviceCatalogImportWriteSupport.ImportRowCreator creator
    ) {
        // Gate：入参
        if (fileBytes == null || fileBytes.length == 0) {
            throw new ValidationException("导入耗材目录失败：文件不能为空");
        }
        if (fileBytes.length > DeviceCatalogImportConstants.ERROR_FILE_MAX_BYTES) {
            throw new ValidationException("导入耗材目录失败：文件过大，请拆分后重试");
        }
        if (orgId == null) {
            throw new ValidationException("导入耗材目录失败：未选择机构上下文");
        }
        if (creator == null) {
            throw new ValidationException("导入耗材目录失败：缺少写入回调");
        }
        String jobId = "DEV-IMP-JOB-" + UUID.randomUUID().toString().replace("-", "");
        DeviceCatalogImportJob job = new DeviceCatalogImportJob();
        job.setJobId(jobId);
        job.setStatus(DeviceCatalogImportJob.STATUS_PENDING);
        job.setPercent(0);
        job.setMessage("排队中");
        job.setFileBytes(fileBytes);
        job.setFileName(fileName);
        job.setOrgId(orgId);
        job.setOperatorId(operatorId);
        job.setTenantId(TenantUtils.getCurrentTenantId());
        jobs.put(jobId, job);
        creators.put(jobId, creator);

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
    public DeviceCatalogImportJob getJob(String jobId) {
        if (StringUtils.isEmpty(jobId)) {
            throw new BusinessException(
                DeviceCatalogImportConstants.ERR_TOKEN,
                "查询导入进度失败：jobId 不能为空");
        }
        DeviceCatalogImportJob job = jobs.get(jobId.trim());
        if (job == null) {
            throw new BusinessException(
                DeviceCatalogImportConstants.ERR_TOKEN,
                "查询导入进度失败：任务不存在或已过期");
        }
        return job;
    }

    private void runJob(String jobId, SecurityContext securityContext) {
        DeviceCatalogImportJob job = jobs.get(jobId);
        if (job == null) {
            return;
        }
        DeviceCatalogImportWriteSupport.ImportRowCreator creator = creators.get(jobId);
        if (creator == null) {
            this.failJob(job, "导入失败：写入回调丢失", null);
            return;
        }
        SecurityContextHolder.setContext(securityContext);
        if (job.getTenantId() != null) {
            tenantContext.setCurrentTenantId(job.getTenantId());
        }
        try {
            job.setStatus(DeviceCatalogImportJob.STATUS_RUNNING);
            this.updateProgress(job, DeviceCatalogImportJob.STAGE_PARSING, 5, 0, 0, "正在解析模板…");
            DeviceCatalogImportWorkbookParser.ParseResult parseResult =
                workbookParser.parse(job.getFileBytes());

            this.updateProgress(job, DeviceCatalogImportJob.STAGE_VALIDATING, 15, 0, 0, "正在校验数据…");
            // Pre：按 Excel 补齐缺失 unit_code，再全量行校验
            conceptEnsureService.ensureMissingConcepts(parseResult.getRows(), terminologyIndex);
            List<ImportDeviceCatalogErrorItemDto> errors = rowValidator.validate(
                parseResult.getRows(),
                job.getOrgId(),
                terminologyIndex,
                deviceDefinitionQueryService);
            if (errors != null && !errors.isEmpty()) {
                String token = errorFileService.buildAndStore(parseResult.getOriginalBytes(), errors);
                job.setErrorFileToken(token);
                job.setErrorCount(errors.size());
                int added = 0;
                for (ImportDeviceCatalogErrorItemDto item : errors) {
                    if (added >= ERROR_MESSAGE_LIMIT) {
                        break;
                    }
                    job.getErrorMessages().add(this.formatErrorLine(item));
                    added = added + 1;
                }
                job.setStatus(DeviceCatalogImportJob.STATUS_FAILED);
                job.setStage(DeviceCatalogImportJob.STAGE_DONE);
                job.setPercent(100);
                job.setMessage("导入失败，共 " + errors.size() + " 处错误，已全部回滚");
                job.setFileBytes(null);
                creators.remove(jobId);
                return;
            }

            List<DeviceCatalogImportRow> rows = parseResult.getRows();
            int total = rows.size();
            job.setTotal(total);
            this.updateProgress(job, DeviceCatalogImportJob.STAGE_WRITING, 20, 0, total, "正在写入目录…");

            String batchNo = DeviceCatalogImportConstants.BATCH_NO_PREFIX
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

            writeSupport.importRows(
                batchNo,
                job.getOperatorId(),
                job.getOrgId(),
                rows,
                creator,
                (processed, rowTotal) -> {
                    int writingPercent = 20 + (int) Math.floor(processed * 75.0 / Math.max(rowTotal, 1));
                    if (writingPercent > 95) {
                        writingPercent = 95;
                    }
                    this.updateProgress(
                        job,
                        DeviceCatalogImportJob.STAGE_WRITING,
                        writingPercent,
                        processed,
                        rowTotal,
                        "正在写入 " + processed + "/" + rowTotal);
                });

            job.setBatchNo(batchNo);
            job.setSuccessCount(total);
            job.setProcessed(total);
            job.setStatus(DeviceCatalogImportJob.STATUS_SUCCEEDED);
            job.setStage(DeviceCatalogImportJob.STAGE_DONE);
            job.setPercent(100);
            job.setMessage("导入完成，共 " + total + " 条");
            job.setFileBytes(null);
            creators.remove(jobId);
            log.info("耗材目录异步导入完成 jobId={}, batchNo={}, success={}", jobId, batchNo, total);
        } catch (ValidationException ex) {
            this.failJob(job, ex.getMessage(), ex.getErrors());
            creators.remove(jobId);
        } catch (BusinessException ex) {
            this.failJob(job, ex.getMessage(), null);
            creators.remove(jobId);
        } catch (Exception ex) {
            log.error("耗材目录异步导入异常 jobId={}", jobId, ex);
            this.failJob(job, "导入失败：" + ex.getMessage(), null);
            creators.remove(jobId);
        } finally {
            tenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void failJob(DeviceCatalogImportJob job, String message, Map<String, String> errors) {
        job.setStatus(DeviceCatalogImportJob.STATUS_FAILED);
        job.setStage(DeviceCatalogImportJob.STAGE_DONE);
        if (job.getPercent() < 100) {
            job.setPercent(100);
        }
        if (StringUtils.isNotEmpty(message)) {
            job.setMessage(message);
        } else {
            job.setMessage("导入失败");
        }
        if (errors != null) {
            String token = errors.get("errorFileToken");
            if (StringUtils.isNotEmpty(token)) {
                job.setErrorFileToken(token);
            }
            int added = 0;
            for (Map.Entry<String, String> entry : errors.entrySet()) {
                if ("errorFileToken".equals(entry.getKey())) {
                    continue;
                }
                if (added >= ERROR_MESSAGE_LIMIT) {
                    break;
                }
                if (StringUtils.isNotEmpty(entry.getValue())) {
                    job.getErrorMessages().add(entry.getValue());
                    added = added + 1;
                }
            }
            job.setErrorCount(Math.max(job.getErrorCount(), added));
        }
        job.setFileBytes(null);
    }

    private void updateProgress(
        DeviceCatalogImportJob job,
        String stage,
        int percent,
        int processed,
        int total,
        String message
    ) {
        job.setStage(stage);
        job.setPercent(percent);
        job.setProcessed(processed);
        if (total > 0) {
            job.setTotal(total);
        }
        job.setMessage(message);
    }

    private String formatErrorLine(ImportDeviceCatalogErrorItemDto item) {
        StringBuilder line = new StringBuilder();
        if (item.getExcelRowNum() != null) {
            line.append("第").append(item.getExcelRowNum()).append("行");
        }
        if (StringUtils.isNotEmpty(item.getColumnName())) {
            if (line.length() > 0) {
                line.append(" ");
            }
            line.append("[").append(item.getColumnName()).append("]");
        }
        if (line.length() > 0) {
            line.append("：");
        }
        if (item.getMessage() != null) {
            line.append(item.getMessage());
        }
        return line.toString();
    }
}
