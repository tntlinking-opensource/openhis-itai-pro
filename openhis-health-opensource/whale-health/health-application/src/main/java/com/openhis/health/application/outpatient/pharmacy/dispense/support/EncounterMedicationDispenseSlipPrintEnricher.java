/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.dispense.support;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.application.outpatient.pharmacy.dispense.dto.EncounterMedicationDispenseSlipRowDto;
import com.openhis.health.application.shared.diagnosis.EncounterDiagnosisSnapshotAssembler;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.openhis.whale.core.application.util.StringUtils.firstNonBlank;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 门诊发药 slip 行处方打印字段回补（患者/机构/诊断/药品展示行）。
 */
@Component
@RequiredArgsConstructor
public class EncounterMedicationDispenseSlipPrintEnricher {

    private final OrganizationQueryService organizationQueryService;
    private final EncounterDiagnosisSnapshotAssembler encounterDiagnosisSnapshotAssembler;

    /**
     * 为门诊发药 slip 行填充处方打印字段。
     *
     * @param rows 已装配 slip 行
     * @param outpatientSnapshot 就诊上下文（非历史医嘱 scope）
     * @param preparerPractitionerNameByDispenseId 调配药师姓名（按发放单）
     */
    public void enrichOutpatientPrintFields(
        List<EncounterMedicationDispenseSlipRowDto> rows,
        OutpatientEncounterContextQueryModel outpatientSnapshot,
        Map<Long, String> preparerPractitionerNameByDispenseId) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        String hospitalName = resolveHospitalName();
        String patientName = null;
        String ageText = null;
        String genderEnumText = null;
        String patientPhone = null;
        String conditionSummaryText = null;
        if (outpatientSnapshot != null) {
            patientName = trimToNull(outpatientSnapshot.getPatientName());
            ageText = trimToNull(outpatientSnapshot.getAgeText());
            genderEnumText = resolveGenderText(outpatientSnapshot.getGenderEnum());
            patientPhone = trimToNull(outpatientSnapshot.getPhone());
            if (outpatientSnapshot.getEncounterId() != null) {
                conditionSummaryText = resolveDiagnosisSummary(outpatientSnapshot.getEncounterId());
            }
        }
        Map<String, BigDecimal> totalPriceByPrescriptionKey = sumTotalPriceByPrescription(rows);
        Map<String, String> prescriptionDateByKey = resolvePrescriptionDateByKey(rows);
        Map<Long, String> preparerNames;
        if (preparerPractitionerNameByDispenseId == null) {
            preparerNames = Map.of();
        } else {
            preparerNames = preparerPractitionerNameByDispenseId;
        }
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row == null) {
                continue;
            }
            row.setPatientName(patientName);
            row.setAgeText(ageText);
            row.setGenderEnumText(genderEnumText);
            row.setPatientPhone(patientPhone);
            row.setHospitalName(hospitalName);
            if (Boolean.TRUE.equals(row.getTcmFlag())) {
                row.setPrescriptionDocumentTitle("中药处方笺");
            } else {
                row.setPrescriptionDocumentTitle("处方笺");
            }
            if (!Boolean.TRUE.equals(row.getTcmFlag()) && StringUtils.isEmpty(row.getConditionSummaryText())) {
                row.setConditionSummaryText(conditionSummaryText);
            }
            applyPrescriptionTypeLines(row);
            String prescriptionKey = prescriptionGroupKey(row);
            BigDecimal prescriptionTotal = totalPriceByPrescriptionKey.get(prescriptionKey);
            row.setPrescriptionTotalPrice(prescriptionTotal);
            row.setPrescriptionTotalPriceText(formatPrescriptionTotalPriceText(prescriptionTotal));
            row.setPrescriptionDateText(prescriptionDateByKey.get(prescriptionKey));
            if (row.getDispenseId() != null) {
                row.setPreparerPractitionerName(preparerNames.get(row.getDispenseId()));
            }
            if (StringUtils.isEmpty(row.getPricingPractitionerName())) {
                row.setPricingPractitionerName(row.getOrderingPractitionerName());
            }
            applyMedicationDisplayFields(row);
        }
    }

    /**
     * 处方类型文案拆成两行（≤4 字首行，余量/中药标记落第二行）。
     *
     * @param row slip 行（就地写入 typeLine1/2）
     */
    private static void applyPrescriptionTypeLines(EncounterMedicationDispenseSlipRowDto row) {
        String typeText = trimToNull(row.getEncounterMedicationTypeText());
        if (typeText == null) {
            row.setPrescriptionTypeLine1(trimToNull(row.getTcmFlagText()));
            row.setPrescriptionTypeLine2(null);
            return;
        }
        if (typeText.length() <= 4) {
            row.setPrescriptionTypeLine1(typeText);
            row.setPrescriptionTypeLine2(trimToNull(row.getTcmFlagText()));
            return;
        }
        int split = Math.min(4, typeText.length());
        row.setPrescriptionTypeLine1(typeText.substring(0, split));
        String line2 = typeText.substring(split).trim();
        if (StringUtils.isEmpty(line2)) {
            line2 = trimToNull(row.getTcmFlagText());
        }
        row.setPrescriptionTypeLine2(line2);
    }

    /**
     * 装配药品左栏（名称+规格+数量）与用法用量展示串。
     *
     * @param row slip 行
     */
    private static void applyMedicationDisplayFields(EncounterMedicationDispenseSlipRowDto row) {
        String name = firstNonBlank(row.getMedicationName(), row.getMerchandiseName());
        String spec = trimToNull(row.getSpecificationText());
        String specPart;
        if (spec == null) {
            specPart = "";
        } else {
            specPart = "(" + spec + ")";
        }
        String qtyPart = formatQuantityWithUnit(row.getQuantity(), row.getUnitText());
        String namePart;
        if (name == null) {
            namePart = "";
        } else {
            namePart = name;
        }
        String left = namePart + specPart;
        if (StringUtils.isNotEmpty(qtyPart)) {
            left = left + " X" + qtyPart;
        }
        row.setMedicationDisplayLeft(StringUtils.trimToEmpty(left));
        row.setMedicationDisplayRight(qtyPart);
        row.setMedicationUsageDisplayText(buildUsageDisplay(row));
    }

    /**
     * 拼接「用法用量：途径 / 频次 / 剂量」展示串。
     *
     * @param row slip 行
     * @return 展示文案；各段皆空则 null
     */
    private static String buildUsageDisplay(EncounterMedicationDispenseSlipRowDto row) {
        List<String> parts = new ArrayList<>();
        String route = trimToNull(row.getRouteText());
        if (route != null) {
            parts.add(route);
        }
        String freq = trimToNull(row.getFrequencyText());
        if (freq != null) {
            parts.add(freq);
        }
        String dosePart = formatDosePart(row.getDoseValue(), row.getDoseUnitText());
        if (dosePart != null) {
            parts.add(dosePart);
        }
        if (parts.isEmpty()) {
            return null;
        }
        return "用法用量：" + String.join(" / ", parts);
    }

    /**
     * 剂量数值与单位拼接（仅单位时返回单位）。
     *
     * @param doseValue    剂量
     * @param doseUnitText 单位文案
     * @return 剂量段；皆空则 null
     */
    private static String formatDosePart(BigDecimal doseValue, String doseUnitText) {
        if (doseValue == null) {
            return trimToNull(doseUnitText);
        }
        String dose = doseValue.stripTrailingZeros().toPlainString();
        String unit = trimToNull(doseUnitText);
        if (unit == null) {
            return dose;
        }
        return dose + unit;
    }

    /**
     * 数量与单位拼接（仅单位时返回单位）。
     *
     * @param quantity 数量
     * @param unitText 单位文案
     * @return 数量段；皆空则 null
     */
    private static String formatQuantityWithUnit(BigDecimal quantity, String unitText) {
        if (quantity == null) {
            return trimToNull(unitText);
        }
        String qty = quantity.stripTrailingZeros().toPlainString();
        String unit = trimToNull(unitText);
        if (unit == null) {
            return qty;
        }
        return qty + unit;
    }

    /**
     * 处方药费合计展示（前缀「药费：」+ 6 位刻度金额）。
     *
     * @param total 处方合计
     * @return 展示串；null 则 null
     */
    private static String formatPrescriptionTotalPriceText(BigDecimal total) {
        if (total == null) {
            return null;
        }
        return "药费：" + MonetaryAmountSupport.scaleDisplay(total).toPlainString();
    }

    /**
     * 按处方分组键汇总各行 totalPrice。
     *
     * @param rows slip 行
     * @return 分组键 → 合计
     */
    private static Map<String, BigDecimal> sumTotalPriceByPrescription(List<EncounterMedicationDispenseSlipRowDto> rows) {
        Map<String, BigDecimal> totals = new HashMap<>();
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row == null) {
                continue;
            }
            String key = prescriptionGroupKey(row);
            BigDecimal lineTotal;
            if (row.getTotalPrice() == null) {
                lineTotal = BigDecimal.ZERO;
            } else {
                lineTotal = row.getTotalPrice();
            }
            totals.merge(key, lineTotal, BigDecimal::add);
        }
        return totals;
    }

    /**
     * 每组处方取最早 requestAuthoredTime 格式化为日期展示。
     *
     * @param rows slip 行
     * @return 分组键 → 日期文案
     */
    private static Map<String, String> resolvePrescriptionDateByKey(List<EncounterMedicationDispenseSlipRowDto> rows) {
        Map<String, LocalDateTime> earliest = new LinkedHashMap<>();
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row == null || row.getRequestAuthoredTime() == null) {
                continue;
            }
            String key = prescriptionGroupKey(row);
            LocalDateTime current = earliest.get(key);
            if (current == null || row.getRequestAuthoredTime().isBefore(current)) {
                earliest.put(key, row.getRequestAuthoredTime());
            }
        }
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, LocalDateTime> entry : earliest.entrySet()) {
            result.put(entry.getKey(), entry.getValue().format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER));
        }
        return result;
    }

    /**
     * 处方汇总分组键：优先 prescriptionNo，否则 dispenseId，再否则行哈希。
     *
     * @param row slip 行
     * @return 非空分组键
     */
    private static String prescriptionGroupKey(EncounterMedicationDispenseSlipRowDto row) {
        String prescriptionNo = trimToNull(row.getPrescriptionNo());
        if (prescriptionNo != null) {
            return prescriptionNo;
        }
        if (row.getDispenseId() != null) {
            return "dispense:" + row.getDispenseId();
        }
        return "row:" + Objects.hash(row.getMedReqId(), row.getMedicationId());
    }

    /**
     * 就诊诊断摘要：优先 conditionDefinitionName，否则解析 diagnosisJson。
     *
     * @param encounterId 就诊
     * @return 诊断摘要；无则 null
     */
    private String resolveDiagnosisSummary(Long encounterId) {
        EncounterDiagnosisSnapshotAssembler.DiagnosisSnapshot snapshot =
            encounterDiagnosisSnapshotAssembler.buildSnapshot(encounterId);
        if (snapshot == null) {
            return null;
        }
        if (StringUtils.isNotEmpty(snapshot.conditionDefinitionName())) {
            return snapshot.conditionDefinitionName();
        }
        return summarizeDiagnosisJson(snapshot.diagnosisJson());
    }

    /**
     * 从诊断 JSON 数组提取病名并以「；」拼接。
     *
     * @param diagnosisJson 快照 JSON
     * @return 摘要；解析失败或无名则 null
     */
    private static String summarizeDiagnosisJson(String diagnosisJson) {
        if (StringUtils.isEmpty(diagnosisJson)) {
            return null;
        }
        try {
            JSONArray array = JSONArray.parseArray(diagnosisJson);
            if (array == null || array.isEmpty()) {
                return null;
            }
            List<String> names = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                JSONObject item = array.getJSONObject(i);
                if (item == null) {
                    continue;
                }
                String name = item.getString(FieldNameConstants.CONDITION_DEFINITION_NAME);
                if (StringUtils.isEmpty(name)) {
                    name = item.getString("name");
                }
                if (StringUtils.isNotEmpty(name)) {
                    names.add(name.trim());
                }
            }
            if (names.isEmpty()) {
                return null;
            }
            return String.join("；", names);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * 机构树中首个启用态 HOSPITAL 类型组织名称。
     *
     * @return 医院名；无则 null
     */
    private String resolveHospitalName() {
        for (Organization organization : organizationQueryService.getOrganizationTree()) {
            if (organization == null || organization.getId() == null) {
                continue;
            }
            if (!PublicationStatus.ACTIVE.getCode().equals(organization.getStatusEnum())) {
                continue;
            }
            if (OrganizationType.HOSPITAL.getCode().equals(organization.getTypeEnum())) {
                return trimToNull(organization.getName());
            }
        }
        return null;
    }

    /**
     * 性别枚举 code → 展示文案；未知则回落原 code。
     *
     * @param genderEnum 性别 code
     * @return 展示文案
     */
    private static String resolveGenderText(String genderEnum) {
        AdministrativeGender gender = EnumUtil.fromCode(AdministrativeGender.class, genderEnum);
        if (gender != null) {
            return gender.getDisplay();
        }
        return trimToNull(genderEnum);
    }
}
