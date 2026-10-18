/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.diagnosiscatalog.support;

import com.zxplat.domain.clinical.conditiondefinition.command.YbConditionImportRowCommand;
import com.zxplat.domain.clinical.conditiondefinition.service.ConditionDefinitionDomainService;
import com.zxplat.domain.share.constants.DoctorStationDiagnosisConstants;
import com.zxplat.domain.infrastructure.integration.insurance.catalog.InsuranceDiseaseCatalogRow;
import com.zxplat.domain.infrastructure.integration.insurance.catalog.InsuranceDiseaseCatalogSourceExtension;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 医保诊断目录异步导入：从 yb_national_catalog_item 按 list_type 灌入对应 sourceEnum。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YbConditionCatalogImportAsyncWorkflow {

    /** sourceEnum → 医保 list_type（对应 13xx 官方下载）。 */
    private static final Map<String, String> SOURCE_TO_LIST_TYPE = Map.of(
        DoctorStationDiagnosisConstants.SOURCE_GENERAL_DX, "1307",
        DoctorStationDiagnosisConstants.SOURCE_CHRONIC_SPEC, "1309",
        DoctorStationDiagnosisConstants.SOURCE_DISEASE_PKG, "1310",
        DoctorStationDiagnosisConstants.SOURCE_PAID_DX, "1310",
        DoctorStationDiagnosisConstants.SOURCE_DAY_SURGERY_DX, "1311",
        DoctorStationDiagnosisConstants.SOURCE_TUMOR_MORPH, "1313",
        DoctorStationDiagnosisConstants.SOURCE_TCM_DX, "1314",
        DoctorStationDiagnosisConstants.SOURCE_TCM_SYNDROME, "1315");

    private static final Map<String, String> LIST_TYPE_LABEL = Map.of(
        "1307", "疾病与诊断目录",
        "1309", "门诊慢特病种目录",
        "1310", "按病种付费病种目录",
        "1311", "日间手术治疗病种目录",
        "1313", "肿瘤形态学目录",
        "1314", "中医疾病目录",
        "1315", "中医证候目录");

    private final TenantContext tenantContext;
    private final InsuranceDiseaseCatalogSourceExtension diseaseCatalogSourceExtension;
    private final ConditionDefinitionDomainService conditionDefinitionDomainService;

    private final ConcurrentHashMap<String, YbConditionCatalogImportJob> jobs = new ConcurrentHashMap<>();

    private final ExecutorService importExecutor = Executors.newFixedThreadPool(1, r -> {
        Thread t = new Thread(r, "yb-condition-catalog-import");
        t.setDaemon(true);
        return t;
    });

    /**
     * 当前来源是否支持从医保导入。
     *
     * @param sourceEnum 目录来源
     * @return true 表示有对应 list_type
     */
    public static boolean supportsSource(String sourceEnum) {
        return resolveListType(sourceEnum) != null;
    }

    /**
     * 启动异步导入。
     *
     * @param sourceEnum 目录来源
     * @return jobId
     */
    public String startImport(String sourceEnum) {
        String source = StringUtils.trimToNull(sourceEnum);
        String listType = resolveListType(source);
        if (listType == null) {
            throw new ValidationException("从医保导入失败：当前诊断目录来源不支持医保导入");
        }
        if (!diseaseCatalogSourceExtension.isEnabled()) {
            throw new ValidationException(
                "从医保导入失败：未启用医保模块，请先启用 CHS 并下载 " + listType + " "
                    + LIST_TYPE_LABEL.getOrDefault(listType, "") + " 目录");
        }
        String jobId = "YB-DX-IMP-" + UUID.randomUUID().toString().replace("-", "");
        YbConditionCatalogImportJob job = new YbConditionCatalogImportJob();
        job.setJobId(jobId);
        job.setSourceEnum(source);
        job.setListType(listType);
        job.setStatus(YbConditionCatalogImportJob.STATUS_PENDING);
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
    public YbConditionCatalogImportJob getJob(String jobId) {
        if (StringUtils.isEmpty(jobId)) {
            throw new BusinessException("YB_CONDITION_IMPORT_001", "查询导入进度失败：jobId 不能为空");
        }
        YbConditionCatalogImportJob job = jobs.get(jobId.trim());
        if (job == null) {
            throw new BusinessException("YB_CONDITION_IMPORT_002", "查询导入进度失败：任务不存在或已过期");
        }
        return job;
    }

    private void runJob(String jobId, SecurityContext securityContext) {
        YbConditionCatalogImportJob job = jobs.get(jobId);
        if (job == null) {
            return;
        }
        SecurityContextHolder.setContext(securityContext);
        if (job.getTenantId() != null) {
            tenantContext.setCurrentTenantId(job.getTenantId());
        }
        String listType = job.getListType();
        String listLabel = LIST_TYPE_LABEL.getOrDefault(listType, listType);
        try {
            job.setStatus(YbConditionCatalogImportJob.STATUS_RUNNING);
            job.setStage(YbConditionCatalogImportJob.STAGE_LOADING);
            job.setPercent(5);
            job.setMessage("正在读取医保" + listLabel + "目录…");

            List<InsuranceDiseaseCatalogRow> sourceRows = diseaseCatalogSourceExtension.listActiveRows(listType);
            List<YbConditionImportRowCommand> rows = toCommands(sourceRows);
            if (rows.isEmpty()) {
                throw new ValidationException(
                    "从医保导入失败：本地无 " + listType + " " + listLabel
                        + " 数据，请先在医保目录下载中拉取该目录");
            }
            job.setTotal(rows.size());
            job.setStage(YbConditionCatalogImportJob.STAGE_WRITING);
            job.setPercent(10);
            job.setMessage("正在写入目录…");

            int success = conditionDefinitionDomainService.importYbConditionCatalog(
                job.getSourceEnum(),
                rows,
                processed -> {
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
            job.setStatus(YbConditionCatalogImportJob.STATUS_SUCCEEDED);
            job.setStage(YbConditionCatalogImportJob.STAGE_DONE);
            job.setPercent(100);
            job.setMessage("导入完成，共写入 " + success + " 条");
            log.info("医保诊断目录导入完成 jobId={}, source={}, listType={}, success={}",
                jobId, job.getSourceEnum(), listType, success);
        } catch (Exception ex) {
            log.error("医保诊断目录导入失败 jobId={}, source={}, listType={}",
                jobId, job.getSourceEnum(), listType, ex);
            job.setStatus(YbConditionCatalogImportJob.STATUS_FAILED);
            job.setStage(YbConditionCatalogImportJob.STAGE_DONE);
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

    private static String resolveListType(String sourceEnum) {
        if (sourceEnum == null || sourceEnum.isBlank()) {
            return null;
        }
        return SOURCE_TO_LIST_TYPE.get(sourceEnum.trim());
    }

    private List<YbConditionImportRowCommand> toCommands(List<InsuranceDiseaseCatalogRow> sourceRows) {
        List<YbConditionImportRowCommand> rows = new ArrayList<>();
        if (sourceRows == null) {
            return rows;
        }
        for (InsuranceDiseaseCatalogRow source : sourceRows) {
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
            YbConditionImportRowCommand cmd = new YbConditionImportRowCommand();
            cmd.setConditionCode(code);
            cmd.setName(name);
            cmd.setPyStr(StringUtils.trimToNull(source.getPinyin()));
            rows.add(cmd);
        }
        return rows;
    }
}
