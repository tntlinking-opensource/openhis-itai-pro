/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.practitionerworkload.support;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadChargeRowQueryModel;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.whale.core.application.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * 工作量处方号 enrich：药品收费行缺 {@code ChargeItem.prescription_no} 时，
 * 批量回落 {@code MedicationRequest.prescription_no}（BR-RPT-WKL-019；只读内存补齐，不写库）。
 *
 * <p>门诊/中药签发挂费草稿历史上常未落收费行处方号，若直接按 {@code service_id} 回落会把同方多味计成多张处方。</p>
 */
@Component
@RequiredArgsConstructor
public class PractitionerWorkloadPrescriptionEnricher {

    /** 药品申请读侧（跨聚合只读；B.6.4 由应用层编排）。 */
    private final MedicationRequestQueryService medicationRequestQueryService;

    /**
     * 对 scope 候选行中「药品请求锚点且收费行无处方号」的明细，内存回填医嘱处方号。
     *
     * @param rows 工作量候选 chargeItem 行；可为空
     */
    public void enrichMissingPrescriptionNoFromMedicationRequest(
        List<PractitionerWorkloadChargeRowQueryModel> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        // Pre：仅收集药品请求锚点且收费行处方号为空的 service_id
        Set<Long> medicationRequestIds = this.collectMedicationRequestIdsMissingPrescriptionNo(rows);
        if (medicationRequestIds.isEmpty()) {
            return;
        }
        // Pre：批量读药品申请，避免按行 N+1
        Map<Long, MedicationRequest> requestById =
            this.medicationRequestQueryService.mapEntitiesByIds(medicationRequestIds);
        for (PractitionerWorkloadChargeRowQueryModel row : rows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            ChargeItem chargeItem = row.getChargeItem();
            if (StringUtils.isNotEmpty(chargeItem.getPrescriptionNo())) {
                continue;
            }
            if (!TableNameConstants.MED_MEDICATION_REQUEST.equals(
                this.trimToEmpty(chargeItem.getServiceTable()))) {
                continue;
            }
            Long serviceId = chargeItem.getServiceId();
            if (serviceId == null) {
                continue;
            }
            MedicationRequest request = requestById.get(serviceId);
            if (request == null || StringUtils.isEmpty(request.getPrescriptionNo())) {
                continue;
            }
            // Asm：仅内存补齐，供 overview 按处方号去重；不落库
            chargeItem.setPrescriptionNo(request.getPrescriptionNo().trim());
        }
    }

    /**
     * 收集需回落处方号的药品申请主键。
     *
     * @param rows 工作量候选行
     * @return 药品申请主键集合（保序去重）
     */
    private Set<Long> collectMedicationRequestIdsMissingPrescriptionNo(
        Collection<PractitionerWorkloadChargeRowQueryModel> rows) {
        LinkedHashSet<Long> medicationRequestIds = new LinkedHashSet<>();
        for (PractitionerWorkloadChargeRowQueryModel row : rows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            ChargeItem chargeItem = row.getChargeItem();
            if (StringUtils.isNotEmpty(chargeItem.getPrescriptionNo())) {
                continue;
            }
            if (!TableNameConstants.MED_MEDICATION_REQUEST.equals(
                this.trimToEmpty(chargeItem.getServiceTable()))) {
                continue;
            }
            if (chargeItem.getServiceId() != null) {
                medicationRequestIds.add(chargeItem.getServiceId());
            }
        }
        return medicationRequestIds;
    }

    /**
     * 去空白；null 视为空串。
     *
     * @param value 原始字符串
     * @return trim 后字符串；null → ""
     */
    private String trimToEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
