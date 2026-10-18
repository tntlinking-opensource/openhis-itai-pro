/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.tracenumber.support;

import com.zxplat.application.report.tracenumber.dto.TraceNumberRecordRowDto;
import com.zxplat.application.report.tracenumber.dto.TraceNumberReportRowDto;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.share.constants.TraceNumberConstants;
import com.zxplat.domain.share.enums.EnumInterface;
import com.zxplat.domain.share.enums.InHospitalStatus;
import com.zxplat.domain.share.enums.SupplyType;
import com.zxplat.domain.share.enums.TraceDirection;
import com.zxplat.domain.share.enums.TraceRecordCategory;
import com.zxplat.domain.share.enums.TraceNumberRecordOperationType;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.domain.terminology.service.CodeSystemQueryService;
import com.zxplat.application.shared.option.TerminologyOptionLoader;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

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
     * Asm：列表行 enrich 药品编码/名称、在院 Label、当前所在库房名。
     *
     * @param rows               列表 DTO
     * @param medicationById     药品定义映射
     * @param locationNameById   库位 id → 名称
     * @param queryScope         查询范围
     */
    public void enrichListRows(
        List<TraceNumberReportRowDto> rows,
        Map<Long, MedicationDefinition> medicationById,
        Map<Long, String> locationNameById,
        String queryScope) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<Long, MedicationDefinition> safeMedicationById = medicationById;
        if (safeMedicationById == null) {
            safeMedicationById = Map.of();
        }
        Map<Long, String> safeLocationNameById = locationNameById;
        if (safeLocationNameById == null) {
            safeLocationNameById = Map.of();
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
                // Asm：规格与概览同源（totalVolume）
                row.setSpecificationText(medication.getTotalVolume());
            }
            InHospitalStatus inHospitalStatus = EnumUtil.fromCode(InHospitalStatus.class, row.getInHospitalStatusEnum());
            if (inHospitalStatus != null) {
                row.setInHospitalStatusLabel(inHospitalStatus.getDisplay());
            }
            // Asm：仅指定库房范围展示「当前所在库房」；全院不关心所在库
            row.setLocationStockStatusEnum(null);
            row.setLocationStockStatusLabel(null);
            if (TraceNumberConstants.REPORT_QUERY_SCOPE_LOCATION.equals(trimToNull(queryScope))) {
                row.setCurrentLocationNames(
                    this.joinLocationNames(row.getCurrentLocationIds(), safeLocationNameById));
            } else {
                row.setCurrentLocationNames(null);
                row.setCurrentLocationIds(null);
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
     * 库位 id 列表 → 名称顿号拼接。
     *
     * @param locationIds        库位 id
     * @param locationNameById   id → 名称
     * @return 展示文案；无命中时 null
     */
    private String joinLocationNames(List<Long> locationIds, Map<Long, String> locationNameById) {
        if (locationIds == null || locationIds.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (Long locationId : locationIds) {
            if (locationId == null) {
                continue;
            }
            String name = locationNameById.get(locationId);
            if (StringUtils.isEmpty(name)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('、');
            }
            builder.append(name.trim());
        }
        if (builder.length() == 0) {
            return null;
        }
        return builder.toString();
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
            TraceRecordCategory flowCategory = EnumUtil.fromCode(TraceRecordCategory.class, row.getFlowCategoryEnum());
            if (flowCategory != null) {
                row.setFlowCategoryLabel(flowCategory.getDisplay());
            }
            TraceDirection flowDirection = EnumUtil.fromCode(TraceDirection.class, row.getFlowDirectionEnum());
            if (flowDirection != null) {
                row.setFlowDirectionLabel(flowDirection.getDisplay());
            }
            if (StringUtils.isNotEmpty(row.getOperationType())) {
                String operationCode = row.getOperationType().trim();
                String operationLabel = operationLabelByCode.get(operationCode);
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
     * 追溯码操作类型 → 展示文案映射。
     *
     * <p>先装 {@link TraceNumberRecordOperationType}（含发药专用码与已声明的供应码），
     * 再以 {@link SupplyType} 补齐供应单驱动落库码（如药库出库 {@code CABINET_STOCK_OUT}）；
     * 同码优先保留追溯枚举文案（如进货「药库进货」优于供应「药库进货单」）。</p>
     *
     * @return code → label
     */
    private Map<String, String> buildOperationTypeLabelMap() {
        Map<String, String> labelByCode = new HashMap<>();
        this.putEnumOptionLabels(labelByCode, TraceNumberRecordOperationType.class);
        this.putEnumOptionLabels(labelByCode, SupplyType.class);
        return labelByCode;
    }

    /**
     * 将枚举 Option 写入 code→label；已有 key 不覆盖（先写入的优先）。
     *
     * @param labelByCode 目标映射
     * @param enumClass   枚举类型
     * @param <E>         实现 {@link EnumInterface} 的枚举
     */
    private <E extends Enum<E> & EnumInterface> void putEnumOptionLabels(
        Map<String, String> labelByCode,
        Class<E> enumClass) {
        List<com.zxplat.framework.core.application.dto.OptionDto> options =
            this.codeSystemQueryService.getOptionListByEnum(enumClass);
        for (com.zxplat.framework.core.application.dto.OptionDto option : options) {
            if (option == null || StringUtils.isEmpty(option.getValue())) {
                continue;
            }
            String code = option.getValue().trim();
            if (labelByCode.containsKey(code)) {
                continue;
            }
            labelByCode.put(code, option.getLabel());
        }
    }
}
