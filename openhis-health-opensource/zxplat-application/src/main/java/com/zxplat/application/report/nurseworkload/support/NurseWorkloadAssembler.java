/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.nurseworkload.support;

import com.zxplat.application.report.nurseworkload.dto.NurseConsumableDetailRowDto;
import com.zxplat.application.report.nurseworkload.dto.NurseConsumableSummaryRowDto;
import com.zxplat.application.report.nurseworkload.dto.NurseExecutionDetailRowDto;
import com.zxplat.application.report.nurseworkload.dto.NurseExecutionSummaryRowDto;
import com.zxplat.application.report.nurseworkload.mapper.NurseWorkloadReportMapper;
import com.zxplat.application.report.nurseworkload.response.SummarizeNurseConsumableWorkloadResponse;
import com.zxplat.application.report.nurseworkload.response.SummarizeNurseExecutionWorkloadResponse;
import com.zxplat.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.zxplat.domain.clinical.procedure.queries.model.NurseExecutionCategorySummaryQueryModel;
import com.zxplat.domain.share.constants.NurseWorkloadConstants;
import com.zxplat.domain.share.enums.GenerateSource;
import com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.zxplat.domain.workflow.devicedispense.queries.model.NurseConsumableHvcmSummaryQueryModel;
import com.zxplat.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import lombok.Getter;
import lombok.Setter;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 护士工作量报表汇总合计与明细 enrich（AS-RPT-002 IF-RPT-NWL-02～06）。
 *
 * <p>只读展示组装；GenerateSource / 类目 / 高值展示名在此回填。</p>
 */
@Component
@RequiredArgsConstructor
public class NurseWorkloadAssembler {

    private final NurseWorkloadReportMapper nurseWorkloadReportMapper;

    /**
     * 组装诊疗执行汇总响应并计算页脚合计（BR-RPT-NWL-016）。
     *
     * @param summaries         领域汇总行
     * @param categoryLabelMap  类目 code → 展示名
     * @return 汇总响应
     */
    public SummarizeNurseExecutionWorkloadResponse buildExecutionSummaryResponse(
        List<NurseExecutionCategorySummaryQueryModel> summaries,
        Map<String, String> categoryLabelMap) {
        SummarizeNurseExecutionWorkloadResponse response = new SummarizeNurseExecutionWorkloadResponse();
        List<NurseExecutionSummaryRowDto> rows = new ArrayList<>();
        long totalExecutionCount = 0L;
        if (summaries != null) {
            for (NurseExecutionCategorySummaryQueryModel summary : summaries) {
                if (summary == null) {
                    continue;
                }
                // 类目展示名经 terminology map 解析，再经 Mapper 落 DTO
                String categoryDisplay = this.resolveCategoryDisplay(summary.getCategoryEnum(), categoryLabelMap);
                NurseExecutionSummaryRowDto row =
                    this.nurseWorkloadReportMapper.toSummaryRowDto(summary, categoryDisplay);
                rows.add(row);
                // 页脚合计为各 category 桶代数累加（BR-RPT-NWL-016）
                totalExecutionCount = totalExecutionCount + summary.getExecutionCount();
            }
        }
        response.setRows(rows);
        response.setTotalExecutionCount(totalExecutionCount);
        return response;
    }

    /**
     * 回填诊疗执行明细展示字段（GenerateSource、项目名、人患就诊等）。
     *
     * @param rows    待 enrich 明细行
     * @param context 批量预加载 map
     */
    public void enrichExecutionDetailRows(
        List<NurseExecutionDetailRowDto> rows,
        NurseWorkloadExecutionDetailEnrichmentContext context) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<String, String> categoryLabelMap = context.getCategoryLabelMap();
        Map<Long, ServiceRequestListModel> serviceRequestById = context.getServiceRequestById();
        Map<Long, ActivityDefinition> activityDefinitionById = context.getActivityDefinitionById();
        Map<Long, String> practitionerNameById = context.getPractitionerNameById();
        Map<Long, String> patientNameById = context.getPatientNameById();
        Map<Long, String> encounterBusNoById = context.getEncounterBusNoById();
        for (NurseExecutionDetailRowDto row : rows) {
            if (row == null) {
                continue;
            }
            // 类目展示名经 activity_category_code 术语 map 回填
            row.setCategoryDisplay(this.resolveCategoryDisplay(row.getCategoryEnum(), categoryLabelMap));
            if (row.getServiceRequestId() != null && serviceRequestById != null) {
                ServiceRequestListModel serviceRequest = serviceRequestById.get(row.getServiceRequestId());
                if (serviceRequest != null) {
                    // GenerateSource 取自 SR.generate_source_enum（BR-RPT-NWL-008）
                    row.setGenerateSourceEnum(serviceRequest.getGenerateSourceEnum());
                    row.setGenerateSourceDisplay(this.resolveGenerateSourceDisplay(serviceRequest.getGenerateSourceEnum()));
                    if (serviceRequest.getActivityId() != null && activityDefinitionById != null) {
                        ActivityDefinition activityDefinition = activityDefinitionById.get(serviceRequest.getActivityId());
                        if (activityDefinition != null && activityDefinition.getName() != null) {
                            row.setItemName(activityDefinition.getName().trim());
                        }
                    }
                }
            }
            if (row.getPractitionerId() != null && practitionerNameById != null) {
                row.setPractitionerName(practitionerNameById.get(row.getPractitionerId()));
            }
            if (row.getPatientId() != null && patientNameById != null) {
                row.setPatientName(patientNameById.get(row.getPatientId()));
            }
            if (row.getEncounterId() != null && encounterBusNoById != null) {
                row.setEncounterBusNo(encounterBusNoById.get(row.getEncounterId()));
            }
        }
    }

    /**
     * 组装耗材使用汇总响应并计算页脚数量合计（BR-RPT-NWL-016）。
     *
     * @param summaries 领域汇总行
     * @return 汇总响应
     */
    public SummarizeNurseConsumableWorkloadResponse buildConsumableSummaryResponse(
        List<NurseConsumableHvcmSummaryQueryModel> summaries) {
        SummarizeNurseConsumableWorkloadResponse response = new SummarizeNurseConsumableWorkloadResponse();
        List<NurseConsumableSummaryRowDto> rows = new ArrayList<>();
        BigDecimal totalQuantity = BigDecimal.ZERO;
        if (summaries != null) {
            for (NurseConsumableHvcmSummaryQueryModel summary : summaries) {
                if (summary == null) {
                    continue;
                }
                NurseConsumableSummaryRowDto row = new NurseConsumableSummaryRowDto();
                row.setHvcmFlag(summary.getHvcmFlag());
                row.setHvcmDisplay(this.resolveHvcmDisplay(summary.getHvcmFlag()));
                row.setQuantityTotal(summary.getQuantityTotal());
                rows.add(row);
                // 页脚数量为各 hvcm 桶 SUM 累加（BR-RPT-NWL-014/016）
                if (summary.getQuantityTotal() != null) {
                    totalQuantity = totalQuantity.add(summary.getQuantityTotal());
                }
            }
        }
        response.setRows(rows);
        response.setTotalQuantity(totalQuantity);
        return response;
    }

    /**
     * 回填耗材使用明细展示字段。
     *
     * @param rows    待 enrich 明细行
     * @param context 批量预加载 map
     */
    public void enrichConsumableDetailRows(
        List<NurseConsumableDetailRowDto> rows,
        NurseWorkloadConsumableDetailEnrichmentContext context) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<Long, DeviceRequestListModel> deviceRequestById = context.getDeviceRequestById();
        Map<Long, DeviceDefinition> deviceDefinitionById = context.getDeviceDefinitionById();
        Map<Long, String> practitionerNameById = context.getPractitionerNameById();
        Map<Long, String> patientNameById = context.getPatientNameById();
        Map<Long, String> encounterBusNoById = context.getEncounterBusNoById();
        Map<String, String> unitLabelMap = context.getUnitLabelMap();
        for (NurseConsumableDetailRowDto row : rows) {
            if (row == null) {
                continue;
            }
            row.setHvcmDisplay(this.resolveHvcmDisplay(row.getHvcmFlag()));
            Long deviceReqId = null;
            if (row.getDispenseId() != null && context.getDeviceReqIdByDispenseId() != null) {
                deviceReqId = context.getDeviceReqIdByDispenseId().get(row.getDispenseId());
            }
            if (deviceReqId != null && deviceRequestById != null) {
                DeviceRequestListModel deviceRequest = deviceRequestById.get(deviceReqId);
                if (deviceRequest != null) {
                    // GenerateSource 取自 DeviceRequest.generate_source_enum（BR-RPT-NWL-015）
                    row.setGenerateSourceEnum(deviceRequest.getGenerateSourceEnum());
                    row.setGenerateSourceDisplay(this.resolveGenerateSourceDisplay(deviceRequest.getGenerateSourceEnum()));
                }
            }
            if (row.getDeviceDefId() != null && deviceDefinitionById != null) {
                DeviceDefinition deviceDefinition = deviceDefinitionById.get(row.getDeviceDefId());
                if (deviceDefinition != null) {
                    if (deviceDefinition.getName() != null) {
                        row.setDeviceName(deviceDefinition.getName().trim());
                    }
                    // 规格展示映射 DeviceDefinition.total_volume（DM-ADM-008）
                    if (deviceDefinition.getTotalVolume() != null) {
                        row.setSpecText(deviceDefinition.getTotalVolume().trim());
                    }
                    // Support 未预解析 hvcm 时，回落目录定义补全高值标志
                    if (row.getHvcmFlag() == null) {
                        row.setHvcmFlag(deviceDefinition.getHvcmFlag());
                        row.setHvcmDisplay(this.resolveHvcmDisplay(deviceDefinition.getHvcmFlag()));
                    }
                }
            }
            if (row.getPractitionerId() != null && practitionerNameById != null) {
                row.setPractitionerName(practitionerNameById.get(row.getPractitionerId()));
            }
            if (row.getPatientId() != null && patientNameById != null) {
                row.setPatientName(patientNameById.get(row.getPatientId()));
            }
            if (row.getEncounterId() != null && encounterBusNoById != null) {
                row.setEncounterBusNo(encounterBusNoById.get(row.getEncounterId()));
            }
            if (row.getUnitCode() != null && unitLabelMap != null) {
                row.setUnitCodeLabel(unitLabelMap.get(row.getUnitCode()));
            }
        }
    }

    /**
     * 解析诊疗类目展示名。
     *
     * @param categoryEnum     类目 code
     * @param categoryLabelMap 术语 map
     * @return 展示名
     */
    private String resolveCategoryDisplay(String categoryEnum, Map<String, String> categoryLabelMap) {
        if (StringUtils.isEmpty(categoryEnum)) {
            return "";
        }
        if (categoryLabelMap != null && categoryLabelMap.containsKey(categoryEnum)) {
            String label = categoryLabelMap.get(categoryEnum);
            if (label != null) {
                return label;
            }
        }
        return categoryEnum;
    }

    /**
     * 解析 GenerateSource 枚举展示名（BR-RPT-NWL-008/015）。
     *
     * @param generateSourceCode 持久化 code
     * @return 展示名；未匹配时回落 code 本身
     */
    private String resolveGenerateSourceDisplay(String generateSourceCode) {
        if (StringUtils.isEmpty(generateSourceCode)) {
            return "";
        }
        for (GenerateSource source : GenerateSource.values()) {
            if (generateSourceCode.equals(source.getCode())) {
                return source.getDisplay();
            }
        }
        return generateSourceCode;
    }

    /**
     * 解析高/低值展示名（BR-RPT-NWL-013）。
     *
     * @param hvcmFlag 高值标志
     * @return 展示文案
     */
    private String resolveHvcmDisplay(Boolean hvcmFlag) {
        if (hvcmFlag == null) {
            return NurseWorkloadConstants.HVCM_UNKNOWN_DISPLAY;
        }
        if (Boolean.TRUE.equals(hvcmFlag)) {
            return NurseWorkloadConstants.HVCM_HIGH_DISPLAY;
        }
        return NurseWorkloadConstants.HVCM_LOW_DISPLAY;
    }

    /**
     * 诊疗执行明细 enrich 批量 map 容器。
     */
    @Getter
    @Setter
    public static class NurseWorkloadExecutionDetailEnrichmentContext {

        /** 类目 code → 展示名。 */
        private Map<String, String> categoryLabelMap;

        /** ServiceRequest id → 列表行。 */
        private Map<Long, ServiceRequestListModel> serviceRequestById;

        /** ActivityDefinition id → 实体。 */
        private Map<Long, ActivityDefinition> activityDefinitionById;

        /** 执业者 id → 姓名。 */
        private Map<Long, String> practitionerNameById;

        /** 患者 id → 姓名。 */
        private Map<Long, String> patientNameById;

        /** 就诊 id → 就诊号。 */
        private Map<Long, String> encounterBusNoById;
    }

    /**
     * 耗材使用明细 enrich 批量 map 容器。
     */
    @Getter
    @Setter
    public static class NurseWorkloadConsumableDetailEnrichmentContext {

        /** 发放 id → 耗材请求 id（明细 QueryModel 未携带 deviceReqId 时的桥接）。 */
        private Map<Long, Long> deviceReqIdByDispenseId;

        /** DeviceRequest id → 列表行。 */
        private Map<Long, DeviceRequestListModel> deviceRequestById;

        /** DeviceDefinition id → 实体。 */
        private Map<Long, DeviceDefinition> deviceDefinitionById;

        /** 执业者 id → 姓名。 */
        private Map<Long, String> practitionerNameById;

        /** 患者 id → 姓名。 */
        private Map<Long, String> patientNameById;

        /** 就诊 id → 就诊号。 */
        private Map<Long, String> encounterBusNoById;

        /** 单位 code → 展示名。 */
        private Map<String, String> unitLabelMap;
    }
}
