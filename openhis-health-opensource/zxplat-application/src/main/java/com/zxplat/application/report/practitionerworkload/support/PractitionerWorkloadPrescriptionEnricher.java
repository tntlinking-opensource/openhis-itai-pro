/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.practitionerworkload.support;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.query.PractitionerWorkloadChargeRowQueryModel;
import com.zxplat.domain.medication.medicationrequest.entity.MedicationRequest;
import com.zxplat.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.framework.core.application.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * 工作量处方号 enrich：药品收费行一律以 {@code MedicationRequest.prescription_no} 为准（BR-RPT-WKL-019）。
 *
 * <p>写入行模型旁路字段供 overview 按方去重；<strong>不</strong>读、不写收费行 {@code prescription_no} 快照。</p>
 */
@Component
@RequiredArgsConstructor
public class PractitionerWorkloadPrescriptionEnricher {

    /** 药品申请读侧（跨聚合只读；B.6.4 由应用层编排）。 */
    private final MedicationRequestQueryService medicationRequestQueryService;

    /**
     * 对 scope 候选行中药品请求锚点明细，用药品申请处方号写入行旁路字段。
     *
     * @param rows 工作量候选 chargeItem 行；可为空
     */
    public void enrichMissingPrescriptionNoFromMedicationRequest(
        List<PractitionerWorkloadChargeRowQueryModel> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        // Pre：收集药品请求锚点 service_id
        Set<Long> medicationRequestIds = this.collectMedicationRequestIds(rows);
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
            // Asm：旁路写入请求真源号；禁止 chargeItem.setPrescriptionNo
            row.setMedicationRequestPrescriptionNo(request.getPrescriptionNo().trim());
        }
    }

    /**
     * 收集药品申请锚点主键。
     *
     * @param rows 工作量候选行
     * @return 药品申请主键集合（保序去重）
     */
    private Set<Long> collectMedicationRequestIds(
        Collection<PractitionerWorkloadChargeRowQueryModel> rows) {
        LinkedHashSet<Long> medicationRequestIds = new LinkedHashSet<>();
        for (PractitionerWorkloadChargeRowQueryModel row : rows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            ChargeItem chargeItem = row.getChargeItem();
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
