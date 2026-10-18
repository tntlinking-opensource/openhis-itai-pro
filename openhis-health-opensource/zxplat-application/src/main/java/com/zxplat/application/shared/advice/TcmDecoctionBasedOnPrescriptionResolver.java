/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.advice;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.zxplat.framework.core.application.util.StringUtils;

import lombok.RequiredArgsConstructor;

import static com.zxplat.framework.core.application.util.StringUtils.trimToEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 代煎 companion {@code ServiceRequest} → 中药方头处方号（展示/归方用）。
 *
 * <p>真源链路：{@code based_on} → {@code MedicationRequest}；若锚在负向退药嘱（含 {@code refund_medicine_id}），
 * 再回原正向药嘱取 {@code CP} 方号，保证列表/发药按原方归并不断档。不读 SR/收费行 {@code prescription_no}。</p>
 */
@Component
@RequiredArgsConstructor
public class TcmDecoctionBasedOnPrescriptionResolver {

    private final ServiceRequestQueryService serviceRequestQueryService;
    private final MedicationRequestQueryService medicationRequestQueryService;

    /**
     * 批量将代煎等诊疗请求主键映射为中药方头处方号。
     *
     * @param serviceRequestIds 诊疗请求主键集合；可空
     * @return serviceRequestId → 方头处方号（通常为正向 {@code CP}）；无法解析时不入表
     */
    public Map<Long, String> mapServiceRequestIdsToHerbalPrescriptionNos(Collection<Long> serviceRequestIds) {
        Map<Long, String> out = new HashMap<>();
        if (serviceRequestIds == null || serviceRequestIds.isEmpty()) {
            return out;
        }
        // Pre：批量装载 SR，避免按行 N+1
        Map<Long, ServiceRequestListModel> byId =
            this.serviceRequestQueryService.mapListModelsByIds(serviceRequestIds);
        Set<Long> basedOnMedicationRequestIds = new LinkedHashSet<>();
        for (ServiceRequestListModel model : byId.values()) {
            if (model == null) {
                continue;
            }
            if (!TableNameConstants.MED_MEDICATION_REQUEST.equals(trimToEmpty(model.getBasedOnTable()))) {
                continue;
            }
            if (model.getBasedOnId() != null) {
                basedOnMedicationRequestIds.add(model.getBasedOnId());
            }
        }
        if (basedOnMedicationRequestIds.isEmpty()) {
            return out;
        }
        // Pre：装载 based_on 药嘱；负向退药嘱再批量回原方
        List<MedicationRequestListQueryModel> medicationRows =
            this.medicationRequestQueryService.listMedicationRequestListModelsByIds(basedOnMedicationRequestIds);
        Map<Long, MedicationRequestListQueryModel> medicationById = new HashMap<>();
        Set<Long> originIds = new LinkedHashSet<>();
        for (MedicationRequestListQueryModel medicationRow : medicationRows) {
            if (medicationRow == null || medicationRow.getId() == null) {
                continue;
            }
            medicationById.put(medicationRow.getId(), medicationRow);
            if (medicationRow.getRefundMedicineId() != null) {
                originIds.add(medicationRow.getRefundMedicineId());
            }
        }
        Map<Long, MedicationRequestListQueryModel> originById = new HashMap<>();
        if (!originIds.isEmpty()) {
            List<MedicationRequestListQueryModel> originRows =
                this.medicationRequestQueryService.listMedicationRequestListModelsByIds(originIds);
            for (MedicationRequestListQueryModel originRow : originRows) {
                if (originRow == null || originRow.getId() == null) {
                    continue;
                }
                originById.put(originRow.getId(), originRow);
            }
        }
        for (Map.Entry<Long, ServiceRequestListModel> entry : byId.entrySet()) {
            ServiceRequestListModel model = entry.getValue();
            if (model == null) {
                continue;
            }
            if (!TableNameConstants.MED_MEDICATION_REQUEST.equals(trimToEmpty(model.getBasedOnTable()))
                || model.getBasedOnId() == null) {
                continue;
            }
            String prescriptionNo = this.resolveHerbalPrescriptionNo(
                medicationById.get(model.getBasedOnId()), originById);
            if (prescriptionNo == null) {
                continue;
            }
            out.put(entry.getKey(), prescriptionNo);
        }
        return out;
    }

    /**
     * 从药嘱读模型解析方头处方号：负向退药嘱回原方，正向直接取号。
     *
     * @param medication  based_on 锚药嘱；可空
     * @param originById  原正向药嘱字典（key=原单 id）
     * @return 方头处方号；无法解析时 {@code null}
     */
    private String resolveHerbalPrescriptionNo(
        MedicationRequestListQueryModel medication,
        Map<Long, MedicationRequestListQueryModel> originById
    ) {
        if (medication == null) {
            return null;
        }
        // Pre：退药嘱方号多为 T+原号，归方展示须回到原正向 CP
        if (medication.getRefundMedicineId() != null) {
            MedicationRequestListQueryModel origin = originById.get(medication.getRefundMedicineId());
            if (origin != null) {
                String fromOrigin = trimToNull(origin.getPrescriptionNo());
                if (fromOrigin != null) {
                    return fromOrigin;
                }
            }
        }
        if (StringUtils.isEmpty(medication.getPrescriptionNo())) {
            return null;
        }
        return medication.getPrescriptionNo().trim();
    }
}
