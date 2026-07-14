/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.tracenumber.support;

import com.openhis.health.application.report.tracenumber.dto.TraceNumberRecordRowDto;
import com.openhis.health.application.report.tracenumber.dto.TraceNumberReportRowDto;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.constants.TraceNumberConstants;
import com.openhis.health.domain.share.enums.InHospitalStatus;
import com.openhis.health.domain.share.enums.LocationStockStatus;
import com.openhis.health.domain.share.enums.TraceDirection;
import com.openhis.health.domain.share.enums.TraceRecordCategory;
import com.openhis.health.domain.share.enums.TraceNumberRecordOperationType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 追溯码报表读侧 enrich：药品名、枚举 Label、流水展示名（AS-CUS-TRC-002 IF-02/04）。
 */
@Component
@RequiredArgsConstructor
public class TraceNumberReportEnrichmentEnricher {

    /** 术语单位展示名。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** 枚举 Option 加载。 */
    private final CodeSystemQueryService codeSystemQueryService;

    /**
     * Asm：列表行 enrich 药品编码/名称与在院、本库状态 Label。
     *
     * @param rows            列表 DTO
     * @param medicationById  药品定义映射
     * @param queryScope      查询范围
     */
    public void enrichListRows(
        List<TraceNumberReportRowDto> rows,
        Map<Long, MedicationDefinition> medicationById,
        String queryScope) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<Long, MedicationDefinition> safeMedicationById = medicationById;
        if (safeMedicationById == null) {
            safeMedicationById = Map.of();
        }
        Map<String, String> unitLabelByCode = this.terminologyOptionLoader.buildLabelMap(
            TerminologyCodeSystemNameConstants.UNIT_CODE);
        for (TraceNumberReportRowDto row : rows) {
            if (row == null) {
                continue;
            }
            MedicationDefinition medication = safeMedicationById.get(row.getMedicationId());
            if (medication != null) {
                row.setMedicationCode(medication.getBusNo());
                row.setMedicationName(medication.getName());
            }
            InHospitalStatus inHospitalStatus = EnumUtil.fromCode(InHospitalStatus.class, row.getInHospitalStatusEnum());
            if (inHospitalStatus != null) {
                row.setInHospitalStatusLabel(inHospitalStatus.getDisplay());
            }
            if (TraceNumberConstants.REPORT_QUERY_SCOPE_LOCATION.equals(trimToNull(queryScope))) {
                LocationStockStatus locationStockStatus =
                    EnumUtil.fromCode(LocationStockStatus.class, row.getLocationStockStatusEnum());
                if (locationStockStatus != null) {
                    row.setLocationStockStatusLabel(locationStockStatus.getDisplay());
                }
            } else {
                row.setLocationStockStatusEnum(null);
                row.setLocationStockStatusLabel(null);
                row.setLocationNetSplitQuantity(null);
            }
            if (StringUtils.isNotEmpty(row.getTraceUnitCode()) && unitLabelByCode != null) {
                String unitLabel = unitLabelByCode.get(row.getTraceUnitCode().trim());
                if (unitLabel != null) {
                    row.setTraceUnitCode(unitLabel);
                }
            }
        }
    }

    /**
     * Asm：流水明细行 enrich 方向/类别/操作类型/单位 Label 与库位名。
     *
     * @param rows              流水 DTO
     * @param locationNameById  库位名映射
     */
    public void enrichFlowLabels(List<TraceNumberRecordRowDto> rows, Map<Long, String> locationNameById) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<Long, String> safeLocationNameById = locationNameById;
        if (safeLocationNameById == null) {
            safeLocationNameById = Map.of();
        }
        Map<String, String> unitLabelByCode = this.terminologyOptionLoader.buildLabelMap(
            TerminologyCodeSystemNameConstants.UNIT_CODE);
        Map<String, String> operationLabelByCode = this.buildOperationTypeLabelMap();
        for (TraceNumberRecordRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getLocationId() != null) {
                row.setLocationName(safeLocationNameById.get(row.getLocationId()));
            }
            TraceRecordCategory flowCategory = EnumUtil.fromCode(TraceRecordCategory.class, row.getRecordCategoryEnum());
            if (flowCategory != null) {
                row.setRecordCategoryLabel(flowCategory.getDisplay());
            }
            TraceDirection flowDirection = EnumUtil.fromCode(TraceDirection.class, row.getRecordDirectionEnum());
            if (flowDirection != null) {
                row.setRecordDirectionLabel(flowDirection.getDisplay());
            }
            if (StringUtils.isNotEmpty(row.getOperationType())) {
                String operationLabel = operationLabelByCode.get(row.getOperationType().trim());
                if (operationLabel != null) {
                    row.setOperationTypeLabel(operationLabel);
                }
            }
            if (StringUtils.isNotEmpty(row.getUnitCode()) && unitLabelByCode != null) {
                String unitLabel = unitLabelByCode.get(row.getUnitCode().trim());
                if (unitLabel != null) {
                    row.setUnitCodeLabel(unitLabel);
                }
            }
        }
    }

    /**
     * 批量加载药品定义 id → 实体映射。
     *
     * @param definitions 药品定义列表
     * @return id → 实体
     */
    public Map<Long, MedicationDefinition> indexMedicationsById(List<MedicationDefinition> definitions) {
        Map<Long, MedicationDefinition> medicationById = new HashMap<>();
        if (definitions == null) {
            return medicationById;
        }
        for (MedicationDefinition definition : definitions) {
            if (definition != null && definition.getId() != null) {
                medicationById.put(definition.getId(), definition);
            }
        }
        return medicationById;
    }

    /**
     * 追溯码操作类型枚举 → 展示文案映射。
     *
     * @return code → label
     */
    private Map<String, String> buildOperationTypeLabelMap() {
        Map<String, String> labelByCode = new HashMap<>();
        List<com.openhis.whale.core.application.dto.OptionDto> options =
            this.codeSystemQueryService.getOptionListByEnum(TraceNumberRecordOperationType.class);
        for (com.openhis.whale.core.application.dto.OptionDto option : options) {
            if (option == null || StringUtils.isEmpty(option.getValue())) {
                continue;
            }
            labelByCode.put(option.getValue().trim(), option.getLabel());
        }
        return labelByCode;
    }
}
