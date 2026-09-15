/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.openhis.health.domain.administration.chargeitem.command.StampChargeItemPrescriptionSnapshotCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * 门诊结算处方号快照盖章：药品从药嘱/发放锚点抄写；诊疗仅申请单（{@code AR}）从 ServiceRequest 抄写。
 *
 * <p>代煎 companion 不写收费行处方号（关联靠 SR.based_on→药嘱，SR 自身处方号应空）。
 * 收费行 {@code prescription_no} 仅为结算快照；药品业务真源在 {@code MedicationRequest}。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientChargePrescriptionSnapshotStamper {

    private final ChargeItemDomainService chargeItemDomainService;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;

    /**
     * 对本批拟结算收费行盖写处方号快照后落库（不改收费状态）。
     *
     * @param chargeItems 拟结算收费行（须已装载）
     * @param operatorId  操作人
     */
    public void stampBeforeSettle(List<ChargeItem> chargeItems, Long operatorId) {
        if (chargeItems == null || chargeItems.isEmpty()) {
            return;
        }
        // Pre：收集药品申请 / 发放 / 诊疗申请锚点，批量反查上游处方号。
        Set<Long> medicationRequestIds = new LinkedHashSet<>();
        Set<Long> dispenseIds = new LinkedHashSet<>();
        Set<Long> serviceRequestIds = new LinkedHashSet<>();
        for (ChargeItem row : chargeItems) {
            if (row == null || row.getId() == null) {
                continue;
            }
            String serviceTable = this.trimToEmpty(row.getServiceTable());
            if (TableNameConstants.MED_MEDICATION_REQUEST.equals(serviceTable) && row.getServiceId() != null) {
                medicationRequestIds.add(row.getServiceId());
            } else if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(serviceTable) && row.getServiceId() != null) {
                dispenseIds.add(row.getServiceId());
            } else if (TableNameConstants.WKF_SERVICE_REQUEST.equals(serviceTable) && row.getServiceId() != null) {
                serviceRequestIds.add(row.getServiceId());
            }
            if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(this.trimToEmpty(row.getDispenseTable()))
                && row.getDispenseId() != null) {
                dispenseIds.add(row.getDispenseId());
            }
        }
        Map<Long, Long> dispenseIdToMedReqId = Map.of();
        if (!dispenseIds.isEmpty()) {
            dispenseIdToMedReqId = this.medicationDispenseQueryService.mapMedicationDispenseIdsToMedReqIds(dispenseIds);
            for (Long medReqId : dispenseIdToMedReqId.values()) {
                if (medReqId != null) {
                    medicationRequestIds.add(medReqId);
                }
            }
        }
        Map<Long, MedicationRequest> medicationById = Map.of();
        if (!medicationRequestIds.isEmpty()) {
            medicationById = this.medicationRequestQueryService.mapEntitiesByIds(medicationRequestIds);
        }
        Map<Long, String> servicePrescriptionById = this.loadServiceRequestPrescriptionNos(serviceRequestIds);

        List<StampChargeItemPrescriptionSnapshotCommand.Item> items = new ArrayList<>();
        for (ChargeItem row : chargeItems) {
            if (row == null || row.getId() == null) {
                continue;
            }
            String prescriptionNo = this.resolveUpstreamPrescriptionNo(
                row, medicationById, dispenseIdToMedReqId, servicePrescriptionById);
            // Pre：无上游号则本行不盖章。
            if (StringUtils.isEmpty(prescriptionNo)) {
                continue;
            }
            StampChargeItemPrescriptionSnapshotCommand.Item item =
                new StampChargeItemPrescriptionSnapshotCommand.Item();
            item.setChargeItemId(row.getId());
            item.setPrescriptionNo(prescriptionNo.trim());
            items.add(item);
        }
        if (items.isEmpty()) {
            return;
        }
        // Cmd：领域写快照列。
        StampChargeItemPrescriptionSnapshotCommand command = new StampChargeItemPrescriptionSnapshotCommand();
        command.setOperatorId(operatorId);
        command.setItems(items);
        this.chargeItemDomainService.stampPrescriptionSnapshots(command);
    }

    /**
     * 批量读取诊疗申请单号（仅 {@code AR} 前缀；代煎等空号/方号不进字典）。
     *
     * @param serviceRequestIds 诊疗申请主键
     * @return 主键 → 申请单号
     */
    private Map<Long, String> loadServiceRequestPrescriptionNos(Collection<Long> serviceRequestIds) {
        Map<Long, String> out = new HashMap<>();
        if (serviceRequestIds == null || serviceRequestIds.isEmpty()) {
            return out;
        }
        Map<Long, ServiceRequestListModel> byId =
            this.serviceRequestQueryService.mapListModelsByIds(serviceRequestIds);
        for (Map.Entry<Long, ServiceRequestListModel> entry : byId.entrySet()) {
            ServiceRequestListModel model = entry.getValue();
            if (model == null || StringUtils.isEmpty(model.getPrescriptionNo())) {
                continue;
            }
            String prescriptionNo = model.getPrescriptionNo().trim();
            // Gate：仅申请单 AR 可盖到收费快照；代煎/皮试等不得把 CP/TCP 写入收费行
            if (!prescriptionNo.startsWith(AggregateTypeConstants.REQUISITION_APPLICATION)) {
                continue;
            }
            out.put(entry.getKey(), prescriptionNo);
        }
        return out;
    }

    /**
     * 从药品请求 / 发放锚点 / 诊疗请求解析上游处方号。
     *
     * @param row                   收费行
     * @param medicationById        药品申请字典
     * @param dispenseIdToMedReqId  发放 → 药品申请
     * @param servicePrescriptionById 诊疗申请处方号
     * @return 上游处方号；无则 null
     */
    private String resolveUpstreamPrescriptionNo(
        ChargeItem row,
        Map<Long, MedicationRequest> medicationById,
        Map<Long, Long> dispenseIdToMedReqId,
        Map<Long, String> servicePrescriptionById
    ) {
        String serviceTable = this.trimToEmpty(row.getServiceTable());
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(serviceTable) && row.getServiceId() != null) {
            return this.prescriptionOfMedication(medicationById.get(row.getServiceId()));
        }
        if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(serviceTable) && row.getServiceId() != null) {
            Long medReqId = dispenseIdToMedReqId.get(row.getServiceId());
            return this.prescriptionOfMedication(medicationById.get(medReqId));
        }
        if (TableNameConstants.WKF_SERVICE_REQUEST.equals(serviceTable) && row.getServiceId() != null) {
            return servicePrescriptionById.get(row.getServiceId());
        }
        if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(this.trimToEmpty(row.getDispenseTable()))
            && row.getDispenseId() != null) {
            Long medReqId = dispenseIdToMedReqId.get(row.getDispenseId());
            return this.prescriptionOfMedication(medicationById.get(medReqId));
        }
        return null;
    }

    /**
     * 读取药品申请处方号。
     *
     * @param request 药品申请；可空
     * @return trim 后处方号；无则 null
     */
    private String prescriptionOfMedication(MedicationRequest request) {
        if (request == null || StringUtils.isEmpty(request.getPrescriptionNo())) {
            return null;
        }
        return request.getPrescriptionNo().trim();
    }

    /**
     * 去空白；null 视为空串。
     *
     * @param value 原始串
     * @return trim 结果
     */
    private String trimToEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
