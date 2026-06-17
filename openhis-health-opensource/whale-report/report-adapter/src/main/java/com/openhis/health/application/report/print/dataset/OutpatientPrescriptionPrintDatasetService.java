/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.application.report.print.dataset;
import com.openhis.health.application.report.support.ReportDatasetTextSupport;

import com.openhis.health.module.common.assembler.inpatient.AdmissionPrimaryDiagnosisAssembler;
import com.openhis.health.module.common.assembler.order.OrderCatalogDisplayAssembler;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.module.querysupport.pharmacy.EncounterMedicationDispenseSlipPageLoader;
import com.openhis.health.module.querysupport.pharmacy.EncounterMedicationDispenseSlipLoaderQuery;
import com.openhis.health.module.querysupport.pharmacy.dto.EncounterMedicationDispenseSlipRowDto;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.report.contracts.prescription.PrescriptionDrugLineGroupSupport;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 门诊处方打印数据集（对齐 {@code outpatient_prescription} 元素包 bindPath）。
 * <p>优先使用发药 slip；无 slip 时回退至就诊内药品医嘱组装。</p>
 */
@Service
@RequiredArgsConstructor
public class OutpatientPrescriptionPrintDatasetService {

    private static final DateTimeFormatter AUTHORED_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final EncounterMedicationDispenseSlipPageLoader encounterMedicationDispenseSlipPageLoader;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final EncounterContextFacade encounterContextFacade;
    private final OrganizationQueryService organizationQueryService;
    private final AdmissionPrimaryDiagnosisAssembler admissionPrimaryDiagnosisAssembler;
    /**
     * 组装打印数据集 root Map（供 Port / 预览 bindPath 绑定）。
     *
     * @param params 业务参数（如 encounterId、paymentId、dateFrom 等）
     * @return 数据集 root；必填参数缺失时返回空 Map
     */

    public Map<String, Object> build(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return Map.of();
        }
        Long encounterId = extractLong(params, "encounterId", "encounter_id");
        if (encounterId == null) {
            return Map.of();
        }
        String prescriptionNo = extractString(params, "prescriptionNo", "prescription_no");
        Long prescriptionGroupId = extractLong(params, "prescriptionGroupId", "prescription_group_id");
        Boolean westernOnly = extractBoolean(params, "westernOnly", "western_only");
        Boolean tcmOnly = extractBoolean(params, "tcmOnly", "tcm_only");
        List<String> dispenseStatusEnumIn = extractStringList(params, "dispenseStatusEnumIn", "dispense_status_enum_in");
        List<Long> medicationRequestIds = extractLongList(params, "medicationRequestIds", "medication_request_ids");

        PrescriptionEncounterSlice encounter = resolveEncounterSlice(encounterId);

        List<EncounterMedicationDispenseSlipRowDto> slipGroupRows = loadSlipGroupRows(
                encounterId,
                prescriptionNo,
                prescriptionGroupId,
                westernOnly,
                tcmOnly,
                dispenseStatusEnumIn,
                medicationRequestIds);
        if (!slipGroupRows.isEmpty()) {
            return toDatasetFromSlips(encounter, slipGroupRows, prescriptionNo);
        }

        List<MedicationRequestListQueryModel> medicationGroupRows = loadMedicationRequestGroupRows(
                encounterId,
                prescriptionNo,
                prescriptionGroupId,
                medicationRequestIds,
                westernOnly,
                tcmOnly);
        if (medicationGroupRows.isEmpty()) {
            return Map.of();
        }
        return toDatasetFromMedicationRequests(encounter, medicationGroupRows, prescriptionNo);
    }

    private List<EncounterMedicationDispenseSlipRowDto> loadSlipGroupRows(
            Long encounterId,
            String prescriptionNo,
            Long prescriptionGroupId,
            Boolean westernOnly,
            Boolean tcmOnly,
            List<String> dispenseStatusEnumIn,
            List<Long> medicationRequestIds) {
        EncounterMedicationDispenseSlipLoaderQuery slipRequest = new EncounterMedicationDispenseSlipLoaderQuery();
        slipRequest.setEncounterId(encounterId);
        slipRequest.setWesternOnly(westernOnly);
        slipRequest.setTcmOnly(tcmOnly);
        if (dispenseStatusEnumIn != null && !dispenseStatusEnumIn.isEmpty()) {
            slipRequest.setDispenseStatusEnumIn(dispenseStatusEnumIn);
        }
        slipRequest.setPageNo(1);
        slipRequest.setPageSize(500);

        List<EncounterMedicationDispenseSlipRowDto> rows =
                encounterMedicationDispenseSlipPageLoader.listSlipRowsForPrescriptionReport(slipRequest);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        Set<Long> medReqIdSet = medicationRequestIds.isEmpty()
                ? Set.of()
                : new LinkedHashSet<>(medicationRequestIds);

        List<EncounterMedicationDispenseSlipRowDto> groupRows = new ArrayList<>();
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (!matchesTcmKindFilterSlip(row, westernOnly, tcmOnly)) {
                continue;
            }
            if (!medReqIdSet.isEmpty()) {
                if (row.getMedReqId() == null || !medReqIdSet.contains(row.getMedReqId())) {
                    continue;
                }
            }
            if (matchesPrescriptionTargetSlip(row, prescriptionNo, prescriptionGroupId)) {
                groupRows.add(row);
            }
        }
        if (!groupRows.isEmpty() && !medReqIdSet.isEmpty()) {
            return expandSlipRowsByPrescriptionKey(rows, groupRows, westernOnly, tcmOnly);
        }
        return groupRows;
    }

    private List<EncounterMedicationDispenseSlipRowDto> expandSlipRowsByPrescriptionKey(
            List<EncounterMedicationDispenseSlipRowDto> allRows,
            List<EncounterMedicationDispenseSlipRowDto> seedRows,
            Boolean westernOnly,
            Boolean tcmOnly) {
        Set<String> keys = seedRows.stream()
                .map(this::prescriptionGroupKeySlip)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return allRows.stream()
                .filter(row -> row != null && keys.contains(prescriptionGroupKeySlip(row)))
                .filter(row -> matchesTcmKindFilterSlip(row, westernOnly, tcmOnly))
                .sorted(Comparator.comparing(
                        EncounterMedicationDispenseSlipRowDto::getRequestAuthoredTime,
                        Comparator.nullsLast(LocalDateTime::compareTo)))
                .toList();
    }

    private String prescriptionGroupKeySlip(EncounterMedicationDispenseSlipRowDto row) {
        boolean herbal = isHerbalSlipRow(row);
        String rxNo = resolveRowRxNo(row);
        return rxNo + "|" + ReportDatasetTextSupport.herbalRxSuffix(herbal);
    }

    private List<MedicationRequestListQueryModel> loadMedicationRequestGroupRows(
            Long encounterId,
            String prescriptionNo,
            Long prescriptionGroupId,
            List<Long> medicationRequestIds,
            Boolean westernOnly,
            Boolean tcmOnly) {
        List<MedicationRequestListQueryModel> all = medicationRequestQueryService
                .listMedicationRequestsByEncounter(encounterId);
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        List<MedicationRequestListQueryModel> forward = all.stream()
                .filter(this::isForwardMedicationRow)
                .sorted(Comparator.comparing(
                        MedicationRequestListQueryModel::getSortNumber,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList();
        if (forward.isEmpty()) {
            return List.of();
        }

        Map<Long, MedicationDefinition> medicationMap = loadMedicationMap(forward);
        List<MedicationRequestListQueryModel> seedRows = resolveMedicationSeedRows(
                forward,
                medicationMap,
                prescriptionNo,
                prescriptionGroupId,
                medicationRequestIds,
                westernOnly,
                tcmOnly);
        if (seedRows.isEmpty()) {
            return List.of();
        }
        Set<String> groupKeys = seedRows.stream()
                .map(row -> prescriptionGroupKeyMedication(row, medicationMap.get(row.getMedicationId())))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return forward.stream()
                .filter(row -> groupKeys.contains(
                        prescriptionGroupKeyMedication(row, medicationMap.get(row.getMedicationId()))))
                .filter(row -> matchesTcmKindFilterMedication(
                        row, medicationMap.get(row.getMedicationId()), westernOnly, tcmOnly))
                .toList();
    }

    private List<MedicationRequestListQueryModel> resolveMedicationSeedRows(
            List<MedicationRequestListQueryModel> forward,
            Map<Long, MedicationDefinition> medicationMap,
            String prescriptionNo,
            Long prescriptionGroupId,
            List<Long> medicationRequestIds,
            Boolean westernOnly,
            Boolean tcmOnly) {
        if (!medicationRequestIds.isEmpty()) {
            Set<Long> idSet = new LinkedHashSet<>(medicationRequestIds);
            return forward.stream()
                    .filter(row -> row.getId() != null && idSet.contains(row.getId()))
                    .filter(row -> matchesTcmKindFilterMedication(
                            row, medicationMap.get(row.getMedicationId()), westernOnly, tcmOnly))
                    .toList();
        }
        List<MedicationRequestListQueryModel> matched = new ArrayList<>();
        for (MedicationRequestListQueryModel row : forward) {
            if (!matchesTcmKindFilterMedication(
                    row, medicationMap.get(row.getMedicationId()), westernOnly, tcmOnly)) {
                continue;
            }
            if (matchesPrescriptionTargetMedication(row, prescriptionNo, prescriptionGroupId)) {
                matched.add(row);
            }
        }
        return matched;
    }

    private Map<Long, MedicationDefinition> loadMedicationMap(List<MedicationRequestListQueryModel> rows) {
        Set<Long> medicationIds = rows.stream()
                .map(MedicationRequestListQueryModel::getMedicationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (medicationIds.isEmpty()) {
            return Map.of();
        }
        return medicationDefinitionQueryService.findAllByIds(medicationIds)
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(MedicationDefinition::getId, item -> item, (a, b) -> a, LinkedHashMap::new));
    }

    private Map<String, Object> toDatasetFromSlips(
            PrescriptionEncounterSlice encounter,
            List<EncounterMedicationDispenseSlipRowDto> groupRows,
            String requestedRxNo) {
        EncounterMedicationDispenseSlipRowDto head = groupRows.get(0);
        Map<String, Object> root = new LinkedHashMap<>();

        String hospitalName = resolveHospitalDisplayName(encounter);
        Map<String, Object> hospital = new LinkedHashMap<>();
        hospital.put("name", hospitalName);
        hospital.put("code", "");
        root.put("hospital", hospital);

        Map<String, Object> patient = buildPatientMap(encounter, head.getEncounterBusNo());
        root.put("patient", patient);

        String rxNo = StringUtils.isNotBlank(requestedRxNo)
                ? requestedRxNo.trim()
                : resolveRowRxNo(head);
        String deptName = StringUtils.defaultIfBlank(head.getOrderingDepartmentName(), "");
        String prescriber = StringUtils.defaultIfBlank(head.getOrderingPractitionerName(), "");
        String diagnosis = groupRows.stream()
                .map(EncounterMedicationDispenseSlipRowDto::getConditionSummaryText)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(resolveDiagnosisSummary(encounter));
        String authored = formatAuthored(head.getRequestAuthoredTime());
        BigDecimal total = groupRows.stream()
                .map(EncounterMedicationDispenseSlipRowDto::getTotalPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String totalAmount = formatMoney(total);

        Map<String, Object> prescription = new LinkedHashMap<>();
        prescription.put("no", rxNo);
        prescription.put("authoredDate", authored);
        prescription.put("deptName", deptName);
        prescription.put("diagnosis", diagnosis);
        prescription.put("totalAmount", totalAmount);
        prescription.put("lines", buildLinesFromSlips(clusterSlipRowsByInfusionGroup(groupRows)));
        root.put("prescription", prescription);

        String pharmacist = groupRows.stream()
                .map(EncounterMedicationDispenseSlipRowDto::getDispensingPractitionerName)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse("");

        root.put("practitioner", buildPractitionerMap(prescriber, pharmacist));
        root.put("footer", buildFooterMap(prescriber, pharmacist, totalAmount));
        return root;
    }

    private Map<String, Object> toDatasetFromMedicationRequests(
            PrescriptionEncounterSlice encounter,
            List<MedicationRequestListQueryModel> groupRows,
            String requestedRxNo) {
        MedicationRequestListQueryModel head = groupRows.get(0);
        Map<Long, MedicationDefinition> medicationMap = loadMedicationMap(groupRows);

        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> hospital = new LinkedHashMap<>();
        hospital.put("name", resolveHospitalDisplayName(encounter));
        hospital.put("code", "");
        root.put("hospital", hospital);
        String fallbackBusNo = null;
        if (encounter != null) {
            fallbackBusNo = encounter.encounterBusNo();
        }
        root.put("patient", buildPatientMap(encounter, fallbackBusNo));

        String rxNo = StringUtils.isNotBlank(requestedRxNo)
                ? requestedRxNo.trim()
                : resolveMedicationRxNo(head);
        String deptName = encounter != null
                ? StringUtils.defaultString(encounter.organizationName(), "")
                : "";
        String prescriber = resolveMedicationPrescriberName(head, encounter);
        String diagnosis = resolveDiagnosisSummary(encounter);
        String authored = formatAuthored(head.getReqAuthoredTime());

        Map<String, Object> prescription = new LinkedHashMap<>();
        prescription.put("no", rxNo);
        prescription.put("authoredDate", authored);
        prescription.put("deptName", deptName);
        prescription.put("diagnosis", diagnosis);
        prescription.put("totalAmount", "");
        prescription.put("lines", buildLinesFromMedicationRequests(
                clusterMedicationRowsByInfusionGroup(groupRows), medicationMap));
        root.put("prescription", prescription);

        root.put("practitioner", buildPractitionerMap(prescriber, ""));
        root.put("footer", buildFooterMap(prescriber, "", ""));
        return root;
    }

    private static Map<String, Object> buildPatientMap(
            PrescriptionEncounterSlice encounter,
            String fallbackBusNo) {
        Map<String, Object> patient = new LinkedHashMap<>();
        if (encounter != null) {
            patient.put("name", StringUtils.defaultString(encounter.patientName(), ""));
            patient.put("gender", toGenderText(encounter.genderEnum()));
            patient.put("age", StringUtils.defaultString(encounter.ageText(), ""));
            patient.put("busNo", StringUtils.defaultString(encounter.encounterBusNo(), ""));
        } else {
            patient.put("name", "");
            patient.put("gender", "");
            patient.put("age", "");
            patient.put("busNo", ReportDatasetTextSupport.stringOrEmpty(fallbackBusNo));
        }
        return patient;
    }

    private static Map<String, Object> buildPractitionerMap(String prescriber, String pharmacist) {
        Map<String, Object> practitioner = new LinkedHashMap<>();
        practitioner.put("prescriberName", prescriber);
        practitioner.put("reviewerName", pharmacist);
        practitioner.put("pharmacistName", pharmacist);
        practitioner.put("dispenserName", pharmacist);
        practitioner.put("checkerName", pharmacist);
        practitioner.put("issuerName", pharmacist);
        return practitioner;
    }

    private static Map<String, Object> buildFooterMap(String prescriber, String pharmacist, String totalAmount) {
        Map<String, Object> footer = new LinkedHashMap<>();
        footer.put("prescriberName", prescriber);
        footer.put("reviewerName", pharmacist);
        footer.put("totalAmount", totalAmount);
        return footer;
    }

    private String resolveDiagnosisSummary(PrescriptionEncounterSlice encounter) {
        if (encounter == null || StringUtils.isBlank(encounter.diagnosisJson())) {
            return "";
        }
        return StringUtils.defaultString(
                admissionPrimaryDiagnosisAssembler.resolveDiagnosisSummary(encounter.diagnosisJson()),
                "");
    }

    private String resolveMedicationPrescriberName(
            MedicationRequestListQueryModel row,
            PrescriptionEncounterSlice encounter) {
        Long requesterId = ReportDatasetTextSupport.coalesceLong(row.getRequesterId(), row.getRecorderId());
        if (requesterId != null) {
            Practitioner practitioner = practitionerQueryService.findById(requesterId);
            if (practitioner != null && StringUtils.isNotBlank(practitioner.getName())) {
                return practitioner.getName().trim();
            }
        }
        if (encounter != null && StringUtils.isNotBlank(encounter.doctorName())) {
            return encounter.doctorName().trim();
        }
        return "";
    }

    private static List<Map<String, Object>> buildLinesFromSlips(List<EncounterMedicationDispenseSlipRowDto> groupRows) {
        List<Map<String, Object>> raw = new ArrayList<>();
        int lineNo = 1;
        for (EncounterMedicationDispenseSlipRowDto row : groupRows) {
            Map<String, Object> line = PrescriptionDrugLineGroupSupport.baseLine(
                    lineNo++,
                    row.getPrescriptionGroupId(),
                    StringUtils.firstNonBlank(row.getMedicationName(), row.getMerchandiseName(), "—"),
                    StringUtils.defaultIfBlank(row.getSpecificationText(), ""),
                    formatSlipQuantity(row),
                    StringUtils.defaultIfBlank(row.getUnitText(), ""),
                    buildSlipUsageText(row),
                    StringUtils.defaultIfBlank(row.getRouteText(), ""),
                    StringUtils.defaultIfBlank(row.getFrequencyText(), ""),
                    StringUtils.defaultIfBlank(row.getSkinTestFlagText(), ""));
            raw.add(line);
        }
        return PrescriptionDrugLineGroupSupport.applyGrouping(raw);
    }

    private static List<Map<String, Object>> buildLinesFromMedicationRequests(
            List<MedicationRequestListQueryModel> groupRows,
            Map<Long, MedicationDefinition> medicationMap) {
        List<Map<String, Object>> raw = new ArrayList<>();
        int lineNo = 1;
        for (MedicationRequestListQueryModel row : groupRows) {
            MedicationDefinition def = row.getMedicationId() != null
                    ? medicationMap.get(row.getMedicationId())
                    : null;
            Map<String, Object> line = PrescriptionDrugLineGroupSupport.baseLine(
                    lineNo++,
                    row.getGroupId(),
                    resolveMedicationDisplayName(def),
                    OrderCatalogDisplayAssembler.resolveMedicationSpecText(def),
                    formatDecimal(row.getQuantity()),
                    StringUtils.defaultString(row.getUnitCode(), ""),
                    buildMedicationUsageText(row),
                    StringUtils.defaultString(row.getMethodCode(), ""),
                    StringUtils.defaultString(row.getRateCode(), ""),
                    ReportDatasetTextSupport.skinTestText(row.getSkinTestFlag()));
            raw.add(line);
        }
        return PrescriptionDrugLineGroupSupport.applyGrouping(raw);
    }

    private static List<MedicationRequestListQueryModel> clusterMedicationRowsByInfusionGroup(
            List<MedicationRequestListQueryModel> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<MedicationRequestListQueryModel> result = new ArrayList<>(rows.size());
        Set<Long> emittedGroups = new LinkedHashSet<>();
        for (MedicationRequestListQueryModel row : rows) {
            Long groupId = row.getGroupId();
            if (groupId == null) {
                result.add(row);
                continue;
            }
            if (emittedGroups.contains(groupId)) {
                continue;
            }
            for (MedicationRequestListQueryModel candidate : rows) {
                if (groupId.equals(candidate.getGroupId())) {
                    result.add(candidate);
                }
            }
            emittedGroups.add(groupId);
        }
        return result;
    }

    private static List<EncounterMedicationDispenseSlipRowDto> clusterSlipRowsByInfusionGroup(
            List<EncounterMedicationDispenseSlipRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<EncounterMedicationDispenseSlipRowDto> result = new ArrayList<>(rows.size());
        Set<Long> emittedGroups = new LinkedHashSet<>();
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            Long groupId = row.getPrescriptionGroupId();
            if (groupId == null) {
                result.add(row);
                continue;
            }
            if (emittedGroups.contains(groupId)) {
                continue;
            }
            for (EncounterMedicationDispenseSlipRowDto candidate : rows) {
                if (groupId.equals(candidate.getPrescriptionGroupId())) {
                    result.add(candidate);
                }
            }
            emittedGroups.add(groupId);
        }
        return result;
    }

    private static String resolveMedicationDisplayName(MedicationDefinition def) {
        if (def != null && StringUtils.isNotBlank(def.getName())) {
            return def.getName().trim();
        }
        return "—";
    }

    private static String buildMedicationUsageText(MedicationRequestListQueryModel row) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.isNotBlank(row.getMethodCode())) {
            parts.add(row.getMethodCode().trim());
        }
        if (row.getDose() != null) {
            String dose = row.getDose().stripTrailingZeros().toPlainString();
            if (StringUtils.isNotBlank(row.getDoseUnitCode())) {
                dose = dose + row.getDoseUnitCode().trim();
            }
            parts.add("每次" + dose);
        }
        if (StringUtils.isNotBlank(row.getRateCode())) {
            parts.add(row.getRateCode().trim());
        }
        if (StringUtils.isNotBlank(row.getDosageInstruction())) {
            parts.add(row.getDosageInstruction().trim());
        }
        return String.join(" ", parts);
    }

    private static String buildSlipUsageText(EncounterMedicationDispenseSlipRowDto row) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.isNotBlank(row.getRouteText())) {
            parts.add(row.getRouteText().trim());
        }
        if (row.getDoseValue() != null) {
            String dose = row.getDoseValue().stripTrailingZeros().toPlainString();
            if (StringUtils.isNotBlank(row.getDoseUnitText())) {
                dose = dose + row.getDoseUnitText().trim();
            }
            parts.add("每次" + dose);
        }
        if (StringUtils.isNotBlank(row.getFrequencyText())) {
            parts.add(row.getFrequencyText().trim());
        }
        return String.join(" ", parts);
    }

    private static String formatSlipQuantity(EncounterMedicationDispenseSlipRowDto row) {
        if (row.getQuantity() == null) {
            return "";
        }
        return row.getQuantity().stripTrailingZeros().toPlainString();
    }

    private static String formatDecimal(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private static String formatAuthored(LocalDateTime time) {
        if (time == null) {
            return "";
        }
        return AUTHORED_FMT.format(time);
    }

    private static String formatMoney(BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            return "";
        }
        return total.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private boolean isForwardMedicationRow(MedicationRequestListQueryModel row) {
        return row != null && row.getId() != null && row.getRefundMedicineId() == null;
    }

    private static boolean matchesTcmKindFilterSlip(
            EncounterMedicationDispenseSlipRowDto row, Boolean westernOnly, Boolean tcmOnly) {
        boolean herbal = isHerbalSlipRow(row);
        if (Boolean.TRUE.equals(westernOnly) && herbal) {
            return false;
        }
        if (Boolean.TRUE.equals(tcmOnly) && !herbal) {
            return false;
        }
        return true;
    }

    private static boolean matchesTcmKindFilterMedication(
            MedicationRequestListQueryModel row,
            MedicationDefinition def,
            Boolean westernOnly,
            Boolean tcmOnly) {
        boolean herbal = isHerbalMedicationRow(row, def);
        if (Boolean.TRUE.equals(westernOnly) && herbal) {
            return false;
        }
        if (Boolean.TRUE.equals(tcmOnly) && !herbal) {
            return false;
        }
        return true;
    }

    private static boolean isHerbalSlipRow(EncounterMedicationDispenseSlipRowDto row) {
        if (Boolean.TRUE.equals(row.getTcmFlag())) {
            return true;
        }
        if (Boolean.FALSE.equals(row.getTcmFlag())) {
            return false;
        }
        String text = StringUtils.defaultString(row.getTcmFlagText(), "")
                + StringUtils.defaultString(row.getMedicationCategoryText(), "");
        return text.contains("中药");
    }

    private static boolean isHerbalMedicationRow(MedicationRequestListQueryModel row, MedicationDefinition def) {
        if (Boolean.TRUE.equals(row.getTcmFlag())) {
            return true;
        }
        if (Boolean.FALSE.equals(row.getTcmFlag())) {
            return false;
        }
        if (def != null && StringUtils.isNotBlank(def.getCategoryCode())) {
            String category = def.getCategoryCode().trim();
            if (category.contains("中药") || "3".equals(category)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPrescriptionTargetSlip(
            EncounterMedicationDispenseSlipRowDto row,
            String prescriptionNo,
            Long prescriptionGroupId) {
        if (StringUtils.isNotBlank(prescriptionNo)) {
            return prescriptionNo.trim().equals(resolveRowRxNo(row));
        }
        if (prescriptionGroupId != null) {
            return prescriptionGroupId.equals(row.getPrescriptionGroupId());
        }
        return true;
    }

    private static boolean matchesPrescriptionTargetMedication(
            MedicationRequestListQueryModel row,
            String prescriptionNo,
            Long prescriptionGroupId) {
        if (StringUtils.isNotBlank(prescriptionNo)) {
            return prescriptionNo.trim().equals(resolveMedicationRxNo(row));
        }
        if (prescriptionGroupId != null) {
            return prescriptionGroupId.equals(row.getGroupId());
        }
        return true;
    }

    private String prescriptionGroupKeyMedication(
            MedicationRequestListQueryModel row,
            MedicationDefinition def) {
        boolean herbal = isHerbalMedicationRow(row, def);
        return resolveMedicationRxNo(row) + "|" + ReportDatasetTextSupport.herbalRxSuffix(herbal);
    }

    /** 处方号仅 {@code med_medication_request.prescription_no}；禁止用发放 busNo 冒充。 */
    private static String resolveRowRxNo(EncounterMedicationDispenseSlipRowDto row) {
        if (StringUtils.isNotBlank(row.getPrescriptionNo())) {
            return row.getPrescriptionNo().trim();
        }
        return "";
    }

    /** 处方号仅药嘱 {@code prescription_no}；禁止用 busNo 或 MR-{id} 冒充。 */
    private static String resolveMedicationRxNo(MedicationRequestListQueryModel row) {
        if (StringUtils.isNotBlank(row.getPrescriptionNo())) {
            return row.getPrescriptionNo().trim();
        }
        return "";
    }

    private String resolveHospitalDisplayName(PrescriptionEncounterSlice encounter) {
        if (encounter == null || encounter.organizationId() == null) {
            return "";
        }
        Long orgId = encounter.organizationId();
        for (int depth = 0; depth < 12; depth++) {
            Organization org = organizationQueryService.loadOrganizationByIdForDisplay(orgId);
            if (org == null) {
                break;
            }
            if (OrganizationType.HOSPITAL.getCode().equals(org.getTypeEnum())
                    && StringUtils.isNotBlank(org.getName())) {
                return org.getName().trim();
            }
            if (org.getParentId() == null) {
                if (StringUtils.isNotBlank(org.getName())) {
                    return org.getName().trim();
                }
                break;
            }
            orgId = org.getParentId();
        }
        return "";
    }

    private PrescriptionEncounterSlice resolveEncounterSlice(Long encounterId) {
        return encounterContextFacade.getOutpatientContextByEncounterId(encounterId)
                .map(PrescriptionEncounterSlice::fromOutpatient)
                .orElseGet(() -> encounterContextFacade.getInpatientContextByEncounterId(encounterId)
                        .map(PrescriptionEncounterSlice::fromInpatient)
                        .orElse(null));
    }

    private record PrescriptionEncounterSlice(
            String patientName,
            String genderEnum,
            String ageText,
            String encounterBusNo,
            String diagnosisJson,
            Long organizationId,
            String organizationName,
            String doctorName) {

        static PrescriptionEncounterSlice fromOutpatient(OutpatientEncounterContextQueryModel encounter) {
            return new PrescriptionEncounterSlice(
                    encounter.getPatientName(),
                    encounter.getGenderEnum(),
                    encounter.getAgeText(),
                    encounter.getEncounterBusNo(),
                    encounter.getDiagnosisJson(),
                    encounter.getOrganizationId(),
                    encounter.getOrganizationName(),
                    encounter.getDoctorName());
        }

        static PrescriptionEncounterSlice fromInpatient(InpatientEncounterContextQueryModel encounter) {
            return new PrescriptionEncounterSlice(
                    encounter.getPatientName(),
                    encounter.getGenderEnum(),
                    encounter.getAgeText(),
                    encounter.getEncounterBusNo(),
                    encounter.getDiagnosisJson(),
                    encounter.getOrganizationId(),
                    encounter.getOrganizationName(),
                    encounter.getAttendingDoctorName());
        }
    }

    private static String toGenderText(String genderEnum) {
        AdministrativeGender g = EnumUtil.fromCode(AdministrativeGender.class, genderEnum);
        if (g != null) {
            return g.getDisplay();
        }
        return ReportDatasetTextSupport.genderCodeOrEmpty(genderEnum);
    }

    private static Long extractLong(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object raw = params.get(key);
            if (raw instanceof Number n) {
                return n.longValue();
            }
            if (raw instanceof String s && StringUtils.isNotBlank(s)) {
                try {
                    return Long.parseLong(s.trim());
                } catch (NumberFormatException ignored) {
                    // next key
                }
            }
        }
        return null;
    }

    private static List<Long> extractLongList(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object raw = params.get(key);
            if (raw instanceof List<?> list) {
                List<Long> out = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Number number) {
                        out.add(number.longValue());
                    } else if (item instanceof String text && StringUtils.isNotBlank(text)) {
                        try {
                            out.add(Long.parseLong(text.trim()));
                        } catch (NumberFormatException ignored) {
                            // skip invalid
                        }
                    }
                }
                return out;
            }
        }
        return List.of();
    }

    private static String extractString(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object raw = params.get(key);
            if (raw instanceof String s && StringUtils.isNotBlank(s)) {
                return s.trim();
            }
        }
        return null;
    }

    private static Boolean extractBoolean(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object raw = params.get(key);
            if (raw instanceof Boolean b) {
                return b;
            }
            if (raw instanceof String s) {
                if ("true".equalsIgnoreCase(s.trim())) {
                    return Boolean.TRUE;
                }
                if ("false".equalsIgnoreCase(s.trim())) {
                    return Boolean.FALSE;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractStringList(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object raw = params.get(key);
            if (raw instanceof List<?> list) {
                List<String> out = new ArrayList<>();
                for (Object item : list) {
                    if (item != null) {
                        String s = item.toString().trim();
                        if (!s.isEmpty()) {
                            out.add(s);
                        }
                    }
                }
                return out;
            }
        }
        return null;
    }
}
