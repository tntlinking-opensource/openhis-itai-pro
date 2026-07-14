/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.tracenumber.support;

import com.openhis.health.application.report.tracenumber.dto.TraceNumberRecordRowDto;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.enums.TraceNumberRecordOperationType;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 追溯码流水明细患者姓名 enrich（TRC-RPT-SDD-R-006/007）。
 *
 * <p>仅 {@code operation_type} 为发药/多发药/退药且 {@code bus_no} 非空时尝试；无关联则患者列为空。</p>
 */
@Component
@RequiredArgsConstructor
public class TraceNumberReportPatientEnricher {

    /** 发放读侧（bus_no 反查）。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /** 患者读侧。 */
    private final PatientQueryService patientQueryService;

    /**
     * Pre：批量 enrich 流水行 {@code patientName}（次要列）。
     *
     * @param rows 流水 DTO（将被原地写入 patientName）
     */
    public void enrichPatientNames(List<TraceNumberRecordRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<String> busNos = new LinkedHashSet<>();
        for (TraceNumberRecordRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (!this.isPatientEnrichEligible(row.getOperationType(), row.getBusNo())) {
                continue;
            }
            busNos.add(row.getBusNo().trim());
        }
        if (busNos.isEmpty()) {
            return;
        }
        List<MedicationDispense> dispenses = this.medicationDispenseQueryService.listByBusNos(busNos);
        Map<String, Long> patientIdByBusNo = new HashMap<>();
        Set<Long> patientIds = new HashSet<>();
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null || StringUtils.isEmpty(dispense.getBusNo())) {
                continue;
            }
            Long patientId = dispense.getPatientId();
            if (patientId == null) {
                continue;
            }
            String normalizedBusNo = dispense.getBusNo().trim();
            if (!patientIdByBusNo.containsKey(normalizedBusNo)) {
                patientIdByBusNo.put(normalizedBusNo, patientId);
                patientIds.add(patientId);
            }
        }
        if (patientIds.isEmpty()) {
            return;
        }
        Map<Long, String> patientNameById = this.patientQueryService.findPatientNamesByIds(patientIds);
        for (TraceNumberRecordRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (!this.isPatientEnrichEligible(row.getOperationType(), row.getBusNo())) {
                continue;
            }
            Long patientId = patientIdByBusNo.get(row.getBusNo().trim());
            if (patientId == null) {
                continue;
            }
            row.setPatientName(patientNameById.get(patientId));
        }
    }

    /**
     * 判定流水行是否属于患者 enrich 白名单操作类型。
     *
     * @param operationType 操作类型 code
     * @param busNo         业务单号
     * @return 可 enrich 时为 true
     */
    private boolean isPatientEnrichEligible(String operationType, String busNo) {
        if (StringUtils.isEmpty(operationType) || trimToNull(busNo) == null) {
            return false;
        }
        String normalizedOperationType = operationType.trim();
        List<String> eligibleCodes = new ArrayList<>();
        eligibleCodes.add(TraceNumberRecordOperationType.MEDICATION_DISPENSE_OUT.getCode());
        eligibleCodes.add(TraceNumberRecordOperationType.MEDICATION_EXTRA_DISPENSE_OUT.getCode());
        eligibleCodes.add(TraceNumberRecordOperationType.MEDICATION_RETURN_IN.getCode());
        for (String eligibleCode : eligibleCodes) {
            if (eligibleCode.equals(normalizedOperationType)) {
                return true;
            }
        }
        return false;
    }
}
