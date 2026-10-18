/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.charge.support;

import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.medication.medicationdispense.entity.MedicationDispense;
import com.zxplat.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.zxplat.domain.medication.medicationrequest.entity.MedicationRequest;
import com.zxplat.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.OutpatientChargeConstants;
import com.zxplat.domain.share.constants.OutpatientNurseStationConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import com.zxplat.domain.share.enums.RequestStatus;
import com.zxplat.domain.workflow.devicedispense.entity.DeviceDispense;
import com.zxplat.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.zxplat.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.zxplat.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门诊正式结算写路径 Gate：收费项 PLANNED、上游医嘱可收费，以及药/耗发放行签发批号已锁定。
 *
 * <p>对应 **AS-OP-FIN-002** IF-OPC-07 / **BR-OPC-023**：禁止「收费定价有批、发放行批号空」进入 SETTLED，
 * 避免护士站执行再报「发放批号缺失」。</p>
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
     * 正式结算前校验：全部收费项须 PLANNED，且锚点医嘱/发放仍处于可收费态（含药耗发放批号已锁定）。
     *
     * @param chargeItems 本次拟结算收费明细（须为最新加载）
     * @throws BusinessException 状态非 PLANNED、锚点失效或发放批号缺失/占位时
     */
    public void assertChargeItemsSettleable(List<ChargeItem> chargeItems) {
        // Gate：先卡收费项自身状态，再校验上游医嘱与发放批号
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
        // Gate：无明细则无需校验
        if (chargeItems == null || chargeItems.isEmpty()) {
            return;
        }
        for (ChargeItem chargeItem : chargeItems) {
            // Skip：空元素不参与状态判定
            if (chargeItem == null) {
                continue;
            }
            // Gate：仅 PLANNED 可进正式结算
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
        // Gate：无明细则无需校验上游
        if (chargeItems == null || chargeItems.isEmpty()) {
            return;
        }
        Set<Long> medReqIds = new LinkedHashSet<>();
        Set<Long> deviceReqIds = new LinkedHashSet<>();
        Set<Long> serviceReqIds = new LinkedHashSet<>();
        List<Long> medicationDispenseIds = new ArrayList<>();
        List<Long> deviceDispenseIds = new ArrayList<>();

        // Pre：收集 service_* 与 dispense_* 双锚点（FIFO 分段挂费以 dispense_* 为计费粒度）
        for (ChargeItem chargeItem : chargeItems) {
            // Skip：缺 service 锚点的行无法校验医嘱态
            if (chargeItem == null || chargeItem.getServiceId() == null || StringUtils.isEmpty(chargeItem.getServiceTable())) {
                continue;
            }
            String serviceTable = chargeItem.getServiceTable();
            Long serviceId = chargeItem.getServiceId();
            // Pre：按请求表归集 ACTIVE 校验集合
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
            // Pre：BR-ORD-016 计费粒度在 dispense_*——签发后药耗行必须从此处收集发放主键做批号门禁
            String dispenseTable = chargeItem.getDispenseTable();
            Long dispenseId = chargeItem.getDispenseId();
            if (dispenseId != null && StringUtils.isNotEmpty(dispenseTable)) {
                if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(dispenseTable)) {
                    medicationDispenseIds.add(dispenseId);
                } else if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(dispenseTable)) {
                    deviceDispenseIds.add(dispenseId);
                }
            }
        }

        // Pre：经发放 QS 反查请求 id，并入 ACTIVE 校验（B.6.4）
        if (!medicationDispenseIds.isEmpty()) {
            Map<Long, Long> dispenseToMedReq =
                medicationDispenseQueryService.mapMedicationDispenseIdsToMedReqIds(medicationDispenseIds);
            medReqIds.addAll(dispenseToMedReq.values());
        }
        if (!deviceDispenseIds.isEmpty()) {
            Map<Long, Long> dispenseToDeviceReq =
                deviceDispenseQueryService.mapDeviceDispenseIdsToDeviceReqIds(deviceDispenseIds);
            deviceReqIds.addAll(dispenseToDeviceReq.values());
        }

        // Gate：请求 ACTIVE + 发放批号锁定
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
        // Gate：无药品请求锚点则跳过
        if (medReqIds.isEmpty()) {
            return;
        }
        List<MedicationRequest> requests = medicationRequestQueryService.listByIds(medReqIds);
        Map<Long, MedicationRequest> byId = new java.util.HashMap<>();
        for (MedicationRequest request : requests) {
            // Skip：空行或无主键不入索引
            if (request != null && request.getId() != null) {
                byId.put(request.getId(), request);
            }
        }
        for (Long medReqId : medReqIds) {
            MedicationRequest request = byId.get(medReqId);
            // Gate：缺失或非 ACTIVE 视为医嘱已变更
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
        // Gate：无耗材请求锚点则跳过
        if (deviceReqIds.isEmpty()) {
            return;
        }
        Map<Long, DeviceRequestListModel> byId = deviceRequestQueryService.mapListModelsByIds(deviceReqIds);
        for (Long deviceReqId : deviceReqIds) {
            DeviceRequestListModel request = byId.get(deviceReqId);
            // Gate：缺失或非 ACTIVE 视为医嘱已变更
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
        // Gate：无诊疗请求锚点则跳过
        if (serviceReqIds.isEmpty()) {
            return;
        }
        Map<Long, ServiceRequestListModel> byId = serviceRequestQueryService.mapListModelsByIds(serviceReqIds);
        for (Long serviceReqId : serviceReqIds) {
            ServiceRequestListModel request = byId.get(serviceReqId);
            // Gate：缺失或非 ACTIVE 视为医嘱已变更
            if (request == null || !RequestStatus.ACTIVE.getCode().equals(request.getStatusEnum())) {
                throw this.adviceChangedException();
            }
        }
    }

    /**
     * Gate：药品发放行须仍存在，且已锁定真实签发批号（与耗材同口径）。
     *
     * @param medicationDispenseIds 药品发放主键列表
     * @throws BusinessException 发放行缺失或批号无效时
     */
    private void assertMedicationDispensesChargeable(List<Long> medicationDispenseIds) {
        for (Long dispenseId : medicationDispenseIds) {
            MedicationDispense dispense = medicationDispenseQueryService.findById(dispenseId);
            // Gate：发放锚点丢失则拒绝结算
            if (dispense == null) {
                throw this.adviceChangedException();
            }
            // Gate：药品 FIFO 签发批号须已落库，禁止仅靠挂费定义批号结算
            this.assertDispenseLotNumberChargeable(dispense.getLotNumber(), "药品");
        }
    }

    /**
     * Gate：耗材发放行须仍存在，且已锁定真实签发批号（禁止空/占位批号结算后护士再报缺失）。
     *
     * @param deviceDispenseIds 耗材发放主键列表
     * @throws BusinessException 发放行缺失或批号无效时
     */
    private void assertDeviceDispensesChargeable(List<Long> deviceDispenseIds) {
        for (Long dispenseId : deviceDispenseIds) {
            DeviceDispense dispense = deviceDispenseQueryService.findById(dispenseId);
            // Gate：发放锚点丢失则拒绝结算，避免收了费却无发放事实
            if (dispense == null) {
                throw this.adviceChangedException();
            }
            // Gate：批号须已由医生站 FIFO 签发锁定；空批号收费是护士「发放批号缺失」的根因之一
            this.assertDispenseLotNumberChargeable(dispense.getLotNumber(), "耗材");
        }
    }

    /**
     * Gate：结算前发放批号须为真实批号（非空、非护士占位）。
     *
     * @param lotNumber    发放行批号
     * @param productLabel 产品类型文案（药品/耗材），写入异常提示
     * @throws BusinessException 批号缺失或为占位时
     */
    private void assertDispenseLotNumberChargeable(String lotNumber, String productLabel) {
        // Gate：空串与 null / 仅空白一律拒绝，避免「收费有批、发放无批」
        if (StringUtils.isEmpty(lotNumber)) {
            throw new BusinessException(
                ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT,
                productLabel + "发放批号缺失，请医生改签后再收费");
        }
        String lotTrim = lotNumber.trim();
        // Gate：护士站占位批号不可作为已收费锁定批号
        if (OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_DEVICE_LOT_PLACEHOLDER.equals(lotTrim)) {
            throw new BusinessException(
                ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT,
                productLabel + "发放批号未锁定，请医生改签后再收费");
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
