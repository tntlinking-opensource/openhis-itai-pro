/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.support;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.OutpatientChargeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门诊正式结算写路径 Gate：收费项 PLANNED 与上游医嘱/发放锚点可收费态（GAP-STALE-001）。
 */
@Component
@RequiredArgsConstructor
public class OutpatientChargeSettlementGate {

    private final MedicationRequestQueryService medicationRequestQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final DeviceRequestQueryService deviceRequestQueryService;
    private final DeviceDispenseQueryService deviceDispenseQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;

    /**
     * 正式结算前校验：全部收费项须 PLANNED，且锚点医嘱/发放仍处于可收费态。
     *
     * @param chargeItems 本次拟结算收费明细（须为最新加载）
     */
    public void assertChargeItemsSettleable(List<ChargeItem> chargeItems) {
        this.assertPlannedChargeItems(chargeItems);
        this.assertAdviceAnchorsChargeable(chargeItems);
    }

    /**
     * Gate：收费明细须均为 PLANNED 态方可结算。
     *
     * @param chargeItems 待结算收费明细
     * @throws BusinessException 存在非 PLANNED 行时
     */
    private void assertPlannedChargeItems(List<ChargeItem> chargeItems) {
        if (chargeItems == null || chargeItems.isEmpty()) {
            return;
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null) {
                continue;
            }
            if (!ChargeItemStatus.PLANNED.getCode().equals(chargeItem.getStatusEnum())) {
                throw this.adviceChangedException();
            }
        }
    }

    /**
     * Gate：按 serviceTable 分流校验医嘱锚点仍 ACTIVE/存在。
     *
     * @param chargeItems 待结算收费明细
     * @throws BusinessException 锚点失效或缺失时
     */
    private void assertAdviceAnchorsChargeable(List<ChargeItem> chargeItems) {
        if (chargeItems == null || chargeItems.isEmpty()) {
            return;
        }
        Set<Long> medReqIds = new LinkedHashSet<>();
        Set<Long> deviceReqIds = new LinkedHashSet<>();
        Set<Long> serviceReqIds = new LinkedHashSet<>();
        List<Long> medicationDispenseIds = new ArrayList<>();
        List<Long> deviceDispenseIds = new ArrayList<>();

        // Pre：收集各聚合锚点 id，供跨聚合 QS 批量校验（B.6.4）
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getServiceId() == null || StringUtils.isEmpty(chargeItem.getServiceTable())) {
                continue;
            }
            String serviceTable = chargeItem.getServiceTable();
            Long serviceId = chargeItem.getServiceId();
            if (TableNameConstants.MED_MEDICATION_REQUEST.equals(serviceTable)) {
                medReqIds.add(serviceId);
            } else if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(serviceTable)) {
                medicationDispenseIds.add(serviceId);
            } else if (TableNameConstants.WKF_DEVICE_REQUEST.equals(serviceTable)) {
                deviceReqIds.add(serviceId);
            } else if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(serviceTable)) {
                deviceDispenseIds.add(serviceId);
            } else if (TableNameConstants.WKF_SERVICE_REQUEST.equals(serviceTable)) {
                serviceReqIds.add(serviceId);
            }
        }

        if (!medicationDispenseIds.isEmpty()) {
            // Pre：经 MedicationDispense QS 反查 medReqId（B.6.4）
            Map<Long, Long> dispenseToMedReq =
                medicationDispenseQueryService.mapMedicationDispenseIdsToMedReqIds(medicationDispenseIds);
            medReqIds.addAll(dispenseToMedReq.values());
        }
        if (!deviceDispenseIds.isEmpty()) {
            Map<Long, Long> dispenseToDeviceReq =
                deviceDispenseQueryService.mapDeviceDispenseIdsToDeviceReqIds(deviceDispenseIds);
            deviceReqIds.addAll(dispenseToDeviceReq.values());
        }

        this.assertMedicationRequestsChargeable(medReqIds);
        this.assertDeviceRequestsChargeable(deviceReqIds);
        this.assertServiceRequestsChargeable(serviceReqIds);
        this.assertMedicationDispensesChargeable(medicationDispenseIds);
        this.assertDeviceDispensesChargeable(deviceDispenseIds);
    }

    /**
     * Gate：药品请求须存在且为 ACTIVE。
     *
     * @param medReqIds 药品请求主键集合
     * @throws BusinessException 请求缺失或非 ACTIVE 时
     */
    private void assertMedicationRequestsChargeable(Set<Long> medReqIds) {
        if (medReqIds.isEmpty()) {
            return;
        }
        List<MedicationRequest> requests = medicationRequestQueryService.listByIds(medReqIds);
        Map<Long, MedicationRequest> byId = new java.util.HashMap<>();
        for (MedicationRequest request : requests) {
            if (request != null && request.getId() != null) {
                byId.put(request.getId(), request);
            }
        }
        for (Long medReqId : medReqIds) {
            MedicationRequest request = byId.get(medReqId);
            if (request == null || !RequestStatus.ACTIVE.getCode().equals(request.getStatusEnum())) {
                throw this.adviceChangedException();
            }
        }
    }

    /**
     * Gate：耗材请求须存在且为 ACTIVE。
     *
     * @param deviceReqIds 耗材请求主键集合
     * @throws BusinessException 请求缺失或非 ACTIVE 时
     */
    private void assertDeviceRequestsChargeable(Set<Long> deviceReqIds) {
        if (deviceReqIds.isEmpty()) {
            return;
        }
        Map<Long, DeviceRequestListModel> byId = deviceRequestQueryService.mapListModelsByIds(deviceReqIds);
        for (Long deviceReqId : deviceReqIds) {
            DeviceRequestListModel request = byId.get(deviceReqId);
            if (request == null || !RequestStatus.ACTIVE.getCode().equals(request.getStatusEnum())) {
                throw this.adviceChangedException();
            }
        }
    }

    /**
     * Gate：诊疗服务请求须存在且为 ACTIVE。
     *
     * @param serviceReqIds 服务请求主键集合
     * @throws BusinessException 请求缺失或非 ACTIVE 时
     */
    private void assertServiceRequestsChargeable(Set<Long> serviceReqIds) {
        if (serviceReqIds.isEmpty()) {
            return;
        }
        Map<Long, ServiceRequestListModel> byId = serviceRequestQueryService.mapListModelsByIds(serviceReqIds);
        for (Long serviceReqId : serviceReqIds) {
            ServiceRequestListModel request = byId.get(serviceReqId);
            if (request == null || !RequestStatus.ACTIVE.getCode().equals(request.getStatusEnum())) {
                throw this.adviceChangedException();
            }
        }
    }

    /**
     * Gate：药品发放行须仍存在（按 dispense id）。
     *
     * @param medicationDispenseIds 药品发放主键列表
     * @throws BusinessException 发放行缺失时
     */
    private void assertMedicationDispensesChargeable(List<Long> medicationDispenseIds) {
        for (Long dispenseId : medicationDispenseIds) {
            MedicationDispense dispense = medicationDispenseQueryService.findById(dispenseId);
            if (dispense == null) {
                throw this.adviceChangedException();
            }
        }
    }

    /**
     * Gate：耗材发放行须仍存在（按 dispense id）。
     *
     * @param deviceDispenseIds 耗材发放主键列表
     * @throws BusinessException 发放行缺失时
     */
    private void assertDeviceDispensesChargeable(List<Long> deviceDispenseIds) {
        for (Long dispenseId : deviceDispenseIds) {
            if (deviceDispenseQueryService.findById(dispenseId) == null) {
                throw this.adviceChangedException();
            }
        }
    }

    /**
     * 构造「医嘱或费用已变更」统一业务异常。
     *
     * @return 预结算 Gate 失败异常
     */
    private BusinessException adviceChangedException() {
        return new BusinessException(
            ErrorCodeConstants.CHARGE_ITEM_INVALID_STATUS,
            OutpatientChargeConstants.MESSAGE_CODE_SETTLE_ADVICE_CHANGED + ": 医嘱或费用已变更，请重新预结算");
    }
}
