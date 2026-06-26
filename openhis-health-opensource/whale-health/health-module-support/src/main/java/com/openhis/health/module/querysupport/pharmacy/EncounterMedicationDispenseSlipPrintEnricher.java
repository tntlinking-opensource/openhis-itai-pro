/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.querysupport.pharmacy;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.module.querysupport.pharmacy.dto.EncounterMedicationDispenseSlipRowDto;
import com.openhis.health.module.shared.diagnosis.EncounterDiagnosisSnapshotAssembler;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 门诊发药 slip 行处方打印字段回补（患者/机构/诊断/药品展示行）。
 */
@Component
public class EncounterMedicationDispenseSlipPrintEnricher {

    private static final DateTimeFormatter PRESCRIPTION_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final OrganizationQueryService organizationQueryService;
    private final EncounterDiagnosisSnapshotAssembler encounterDiagnosisSnapshotAssembler;

    public EncounterMedicationDispenseSlipPrintEnricher(
        OrganizationQueryService organizationQueryService,
        EncounterDiagnosisSnapshotAssembler encounterDiagnosisSnapshotAssembler) {
        this.organizationQueryService = organizationQueryService;
        this.encounterDiagnosisSnapshotAssembler = encounterDiagnosisSnapshotAssembler;
    }

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
        Map<Long, String> preparerNames = preparerPractitionerNameByDispenseId == null
            ? Map.of()
            : preparerPractitionerNameByDispenseId;
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row == null) {
                continue;
            }
            row.setPatientName(patientName);
            row.setAgeText(ageText);
            row.setGenderEnumText(genderEnumText);
            row.setPatientPhone(patientPhone);
            row.setHospitalName(hospitalName);
            row.setPrescriptionDocumentTitle("处方笺");
            if (StringUtils.isBlank(row.getConditionSummaryText())) {
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
            if (StringUtils.isBlank(row.getPricingPractitionerName())) {
                row.setPricingPractitionerName(row.getOrderingPractitionerName());
            }
            applyMedicationDisplayFields(row);
        }
    }

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
        if (StringUtils.isBlank(line2)) {
            line2 = trimToNull(row.getTcmFlagText());
        }
        row.setPrescriptionTypeLine2(line2);
    }

    private static void applyMedicationDisplayFields(EncounterMedicationDispenseSlipRowDto row) {
        String name = firstNonBlank(row.getMedicationName(), row.getMerchandiseName());
        String spec = trimToNull(row.getSpecificationText());
        String specPart = spec == null ? "" : "(" + spec + ")";
        String qtyPart = formatQuantityWithUnit(row.getQuantity(), row.getUnitText());
        String left = (name == null ? "" : name) + specPart;
        if (StringUtils.isNotBlank(qtyPart)) {
            left = left + " X" + qtyPart;
        }
        row.setMedicationDisplayLeft(StringUtils.trimToEmpty(left));
        row.setMedicationDisplayRight(qtyPart);
        row.setMedicationUsageDisplayText(buildUsageDisplay(row));
    }

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

    private static String formatPrescriptionTotalPriceText(BigDecimal total) {
        if (total == null) {
            return null;
        }
        return "药费：" + total.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static Map<String, BigDecimal> sumTotalPriceByPrescription(List<EncounterMedicationDispenseSlipRowDto> rows) {
        Map<String, BigDecimal> totals = new HashMap<>();
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row == null) {
                continue;
            }
            String key = prescriptionGroupKey(row);
            BigDecimal lineTotal = row.getTotalPrice() == null ? BigDecimal.ZERO : row.getTotalPrice();
            totals.merge(key, lineTotal, BigDecimal::add);
        }
        return totals;
    }

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
            result.put(entry.getKey(), entry.getValue().format(PRESCRIPTION_DATE_FMT));
        }
        return result;
    }

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

    private String resolveDiagnosisSummary(Long encounterId) {
        EncounterDiagnosisSnapshotAssembler.DiagnosisSnapshot snapshot =
            encounterDiagnosisSnapshotAssembler.buildSnapshot(encounterId);
        if (snapshot == null) {
            return null;
        }
        if (StringUtils.isNotBlank(snapshot.conditionDefinitionName())) {
            return snapshot.conditionDefinitionName();
        }
        return summarizeDiagnosisJson(snapshot.diagnosisJson());
    }

    private static String summarizeDiagnosisJson(String diagnosisJson) {
        if (StringUtils.isBlank(diagnosisJson)) {
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
                if (StringUtils.isBlank(name)) {
                    name = item.getString("name");
                }
                if (StringUtils.isNotBlank(name)) {
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

    private static String resolveGenderText(String genderEnum) {
        AdministrativeGender gender = EnumUtil.fromCode(AdministrativeGender.class, genderEnum);
        if (gender != null) {
            return gender.getDisplay();
        }
        return trimToNull(genderEnum);
    }

    private static String firstNonBlank(String first, String second) {
        String a = trimToNull(first);
        if (a != null) {
            return a;
        }
        return trimToNull(second);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }
}
