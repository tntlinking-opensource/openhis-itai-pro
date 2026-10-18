/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.orderexecute.support;

import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingParentRequestAnchorDto;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingWriteItemDto;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.mapper.NurseStationInpatientBillingMapper;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.support.NurseStationInpatientBillingProcedureOrchestrator;
import com.zxplat.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteBindItemDto;
import com.zxplat.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteWriteSlotDto;
import com.zxplat.application.inpatient.nursestation.orderexecute.mapper.NurseStationOrderExecuteMapper;
import com.zxplat.application.shared.activitydefinition.ActivityDefinitionPackageChargePersistSupport;
import com.zxplat.application.shared.advice.InpatientOrderListSkeleton;
import com.zxplat.application.shared.nursestation.DeviceAutoInventoryDeductSettingResolver;
import com.zxplat.application.shared.nursestation.DeviceDispenseNurseWorkflowOrchestrator;
import com.zxplat.application.shared.product.DoctorStationAdviceFifoAllocationAssembler;
import com.zxplat.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.zxplat.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.zxplat.domain.share.constants.ReadModelOrderTypeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.workflow.devicedispense.queries.model.DeviceDispenseListModel;
import com.zxplat.domain.workflow.devicedispense.service.DeviceDispenseDomainService;
import com.zxplat.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.zxplat.domain.workflow.devicerequest.service.DeviceRequestDomainService;
import com.zxplat.domain.workflow.servicerequest.command.CreateFromNursePricingCommand;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 住院护士站医嘱执行：绑定项（用法/诊疗带出）按医嘱补费同构落库（AS-IP-NUR-004 BR-IP-EXE-019）。
 *
 * <p>产出 {@code generate_source=nurse-pricing} 且 {@code based_on} 挂主医嘱，与 IF-IP-PRC-04
 * 手动补费一致，可在「医嘱计费 / 已划价」看见并冲销；不再写 {@code order-execute} 派生费。</p>
 */
@Component
@RequiredArgsConstructor
public class NurseStationOrderExecuteBindPricingOrchestrator {

    /** 护士划价诊疗建单。 */
    private final ServiceRequestDomainService serviceRequestDomainService;
    /** 护士划价诊疗 Procedure 三态。 */
    private final NurseStationInpatientBillingProcedureOrchestrator nurseStationInpatientBillingProcedureOrchestrator;
    /** 补费写命令装配（based_on / Charge）。 */
    private final NurseStationInpatientBillingMapper nurseStationInpatientBillingMapper;
    /** 执行侧绑定命令装配（定价/耗材请求）。 */
    private final NurseStationOrderExecuteMapper nurseStationOrderExecuteMapper;
    /** 收费写侧。 */
    private final ChargeItemDomainService chargeItemDomainService;
    /** 套餐子项账单。 */
    private final ActivityDefinitionPackageChargePersistSupport activityDefinitionPackageChargePersistSupport;
    /** 耗材请求写侧。 */
    private final DeviceRequestDomainService deviceRequestDomainService;
    /** 耗材发放写侧。 */
    private final DeviceDispenseDomainService deviceDispenseDomainService;
    /** 耗材发放读侧。 */
    private final DeviceDispenseQueryService deviceDispenseQueryService;
    /** 住院临时耗材发放收口。 */
    private final DeviceDispenseNurseWorkflowOrchestrator deviceDispenseNurseWorkflowOrchestrator;
    /** 自动扣库开关。 */
    private final DeviceAutoInventoryDeductSettingResolver deviceAutoInventoryDeductSettingResolver;
    /** FIFO 预占（自动扣库开时）。 */
    private final DoctorStationAdviceFifoAllocationAssembler doctorStationAdviceFifoAllocationAssembler;

    /**
     * 绑定诊疗：按医嘱补费写核落库（nurse-pricing + based_on 主医嘱）。
     *
     * <p>Procedure.occurrence / recorded 均锚 {@code planTime}，供取消执行按计划点关联冲销，
     * 避免误伤同父医嘱上的手动补费行（通常 occurrence≈划价时刻）。</p>
     *
     * @param item              主医嘱写槽位
     * @param order             主医嘱骨架
     * @param bindItem          绑定诊疗项
     * @param quantity          带出数量（已 &gt; 0）
     * @param unitCode          单位；可空由域回落
     * @param priceLine         价目行
     * @param activityProduct   诊疗产品读模型；可空
     * @param operatorId        操作人
     * @param performerId       执行人 / 申请人
     * @param loginWorkOrgId    登录工作科室（Procedure.org_id）
     * @param orgId             就诊机构（Charge.org_id）
     */
    public void priceBindActivityAsNurseSupplement(
        NurseStationOrderExecuteWriteSlotDto item,
        InpatientOrderListSkeleton order,
        NurseStationOrderExecuteBindItemDto bindItem,
        BigDecimal quantity,
        String unitCode,
        ChargeItemDefinition priceLine,
        ProductDefinitionDisplayDto activityProduct,
        Long operatorId,
        Long performerId,
        Long loginWorkOrgId,
        Long orgId) {
        // Pre：组装与 IF-IP-PRC-04 同构的补费写行（父锚点=主医嘱）
        NurseStationPricingWriteItemDto pricingRow = this.buildActivityPricingRow(
            item, order, bindItem, quantity, unitCode, priceLine);
        CreateFromNursePricingCommand serviceCmd =
            nurseStationInpatientBillingMapper.toCreateFromNursePricingCommand(
                List.of(pricingRow), operatorId, performerId);
        // Cmd：护士划价诊疗建单（generate_source=nurse-pricing，态 VERIFIED）
        List<Long> serviceRequestIds = serviceRequestDomainService.createFromNursePricing(serviceCmd);
        Long serviceRequestId = serviceRequestIds.get(0);
        // Cmd：划价即完成 Procedure；occurrence 用计划点，取消执行按槽关联
        LocalDateTime slotPlanTime = item.getPlanTime();
        Long procedureId = nurseStationInpatientBillingProcedureOrchestrator.registerCompletedNursePricingProcedure(
            serviceRequestId,
            order.getEncounterId(),
            order.getPatientId(),
            performerId,
            slotPlanTime,
            loginWorkOrgId);
        // Cmd：护士划价待结算 Charge（与手动补费同 API，可进已划价/冲销）
        CreateChargeItemCommand chargeCmd =
            nurseStationOrderExecuteMapper.toCreateNursePricingChargeItemFromBindCommand(
                order.getPatientId(),
                order.getEncounterId(),
                TableNameConstants.WKF_SERVICE_REQUEST,
                serviceRequestId,
                TableNameConstants.WKF_ACTIVITY_DEFINITION,
                bindItem.getActivityId(),
                priceLine,
                item.getAccountId(),
                procedureId,
                slotPlanTime,
                quantity,
                unitCode,
                activityProduct,
                null,
                null,
                operatorId,
                orgId);
        Long parentChargeId = chargeItemDomainService.priceByNurseStation(chargeCmd);
        // Cmd：带子项诊疗冻价写子账单
        activityDefinitionPackageChargePersistSupport.persistPackageChildChargeItemsAfterParent(
            parentChargeId, bindItem.getActivityId(), operatorId);
    }

    /**
     * 绑定耗材：按医嘱补费写核落库（nurse-pricing DeviceRequest + 发放 + Charge）。
     *
     * <p>Charge.{@code procedure_id} 挂主医嘱 Procedure，取消该槽时按 procedure 定位冲销，
     * 不误伤同父医嘱、{@code procedure_id} 为空的手动补费耗材行。</p>
     *
     * @param item                 主医嘱写槽位
     * @param order                主医嘱骨架
     * @param bindItem             绑定耗材项
     * @param quantity             带出数量
     * @param unitCode             单位
     * @param deviceProduct        耗材产品读模型；可空
     * @param performLocation      发放库房
     * @param packageId            组套 id；可空
     * @param priceLine            价目行（调用方已按批号重算时可传入）
     * @param preferredDefinitionId 无实发批号时可用的绑定项 definitionId；可空
     * @param operatorId           操作人
     * @param performerId          执行人
     * @param parentProcedureId    主医嘱 Procedure（Charge 关联）
     * @param orgId                机构
     * @param inventoryDecreaseAcc 库存扣减累计；可空则本方法内新建
     * @param priceLineResolver    实发批号后重定价回调
     */
    public void priceBindDeviceAsNurseSupplement(
        NurseStationOrderExecuteWriteSlotDto item,
        InpatientOrderListSkeleton order,
        NurseStationOrderExecuteBindItemDto bindItem,
        BigDecimal quantity,
        String unitCode,
        ProductDefinitionDisplayDto deviceProduct,
        Long performLocation,
        Long packageId,
        ChargeItemDefinition priceLine,
        Long preferredDefinitionId,
        Long operatorId,
        Long performerId,
        Long parentProcedureId,
        Long orgId,
        Map<Long, BigDecimal> inventoryDecreaseAcc,
        BindDevicePriceLineResolver priceLineResolver) {
        Long deviceDefId = bindItem.getDeviceId();
        // Pre：自动扣库开时先 FIFO 预占，与手动补费同构
        if (deviceAutoInventoryDeductSettingResolver.isEnabled()) {
            doctorStationAdviceFifoAllocationAssembler.allocateDeviceFifoAtPerformLocation(
                order.getEncounterId(),
                performLocation,
                deviceDefId,
                unitCode,
                quantity);
        }
        String basedOnTable = this.resolveRequestTable(item.getOrderType());
        // Cmd：护士划价耗材请求（generate_source=nurse-pricing + based_on 主医嘱）
        List<Long> deviceReqIds = deviceRequestDomainService.createExecutedDeviceOrder(
            nurseStationOrderExecuteMapper.toCreateExecutedDeviceOrderCommand(
                deviceDefId,
                quantity,
                unitCode,
                performLocation,
                order.getEncounterId(),
                order.getPatientId(),
                basedOnTable,
                item.getOrderId(),
                packageId,
                operatorId));
        Long deviceReqId = deviceReqIds.get(0);
        deviceDispenseDomainService.generateDeviceDispenseFromTemporaryPath(
            nurseStationOrderExecuteMapper.toGenerateDeviceDispenseFromTemporaryPathCommand(
                deviceReqId,
                deviceDefId,
                order.getEncounterId(),
                order.getPatientId(),
                quantity,
                unitCode,
                item.getPlanTime(),
                operatorId));
        Map<Long, BigDecimal> decreaseAcc = inventoryDecreaseAcc;
        // 调用方未传累计器时本方法内新建，避免 NPE；同批执行仍应由 App 传入以合并扣库
        if (decreaseAcc == null) {
            decreaseAcc = new HashMap<>();
        }
        // Cmd：划价两轨收口（开关关=待发；开=已发+扣库累计）
        deviceDispenseNurseWorkflowOrchestrator.finalizeInpatientTemporaryPathDispense(
            deviceReqId, operatorId, performerId, decreaseAcc);
        DeviceDispenseListModel primaryDispense =
            deviceDispenseQueryService.resolvePrimaryDispenseForDeviceRequest(deviceReqId);
        Long dispenseId = null;
        String executionLotNumber = null;
        // 有主发放行时挂 Charge.dispense；批号供 LOT_NUMBER_PRICE 重定价
        if (primaryDispense != null) {
            dispenseId = primaryDispense.getId();
            executionLotNumber = primaryDispense.getLotNumber();
        }
        // Pre：有实发批号时按批号重定价，禁止串用绑定项可能过期的 definitionId
        ChargeItemDefinition effectivePriceLine = priceLine;
        if (priceLineResolver != null) {
            Long preferredId = preferredDefinitionId;
            // 实发批号优先批号价；清空 preferred，避免串批 definitionId
            if (StringUtils.isNotEmpty(executionLotNumber)) {
                preferredId = null;
            }
            effectivePriceLine = priceLineResolver.resolve(deviceDefId, preferredId, executionLotNumber);
        }
        // Cmd：护士划价 Charge；procedure 挂主医嘱槽，供取消执行按槽冲销
        CreateChargeItemCommand chargeCmd =
            nurseStationOrderExecuteMapper.toCreateNursePricingChargeItemFromBindCommand(
                order.getPatientId(),
                order.getEncounterId(),
                TableNameConstants.WKF_DEVICE_REQUEST,
                deviceReqId,
                TableNameConstants.ADM_DEVICE_DEFINITION,
                deviceDefId,
                effectivePriceLine,
                item.getAccountId(),
                parentProcedureId,
                item.getPlanTime(),
                quantity,
                unitCode,
                deviceProduct,
                dispenseId,
                TableNameConstants.WKF_DEVICE_DISPENSE,
                operatorId,
                orgId);
        chargeItemDomainService.priceByNurseStation(chargeCmd);
    }

    /**
     * 组装绑定诊疗对应的护士补费写行。
     *
     * @param item      写槽位
     * @param order     主医嘱
     * @param bindItem  绑定项
     * @param quantity  数量
     * @param unitCode  单位
     * @param priceLine 价目
     * @return 补费写行
     */
    private NurseStationPricingWriteItemDto buildActivityPricingRow(
        NurseStationOrderExecuteWriteSlotDto item,
        InpatientOrderListSkeleton order,
        NurseStationOrderExecuteBindItemDto bindItem,
        BigDecimal quantity,
        String unitCode,
        ChargeItemDefinition priceLine) {
        NurseStationPricingWriteItemDto row = new NurseStationPricingWriteItemDto();
        row.setEncounterId(order.getEncounterId());
        row.setPatientId(order.getPatientId());
        row.setAccountId(item.getAccountId());
        row.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        row.setProductDefinitionId(bindItem.getActivityId());
        row.setActivityId(bindItem.getActivityId());
        row.setQuantity(quantity);
        row.setUnitCode(unitCode);
        // Pre：价目主键供 based_on 建单追溯；金额由后续 priceByNurseStation + OrderChargePricing 落定
        if (priceLine != null) {
            row.setChargeItemDefinitionId(priceLine.getId());
            row.setUnitPrice(priceLine.getPriceValue());
        } else {
            // 价目未预热时回落绑定项透传价（极端缺价目仍尽量建单）
            row.setChargeItemDefinitionId(bindItem.getChargeItemDefinitionId());
            row.setUnitPrice(bindItem.getUnitPrice());
        }
        BigDecimal unitPrice = row.getUnitPrice();
        // 缺单价按 0 汇总，避免 NPE；真实金额以 priceByNurseStation 为准
        if (unitPrice == null) {
            unitPrice = BigDecimal.ZERO;
        }
        row.setTotalPrice(unitPrice.multiply(quantity));
        NurseStationPricingParentRequestAnchorDto anchor = new NurseStationPricingParentRequestAnchorDto();
        anchor.setOrderType(item.getOrderType());
        anchor.setRequestId(item.getOrderId());
        row.setParentRequestAnchor(anchor);
        return row;
    }

    /**
     * 写槽位 orderType → 物理 based_on 表名（诊嘱→服务申请表，否则药嘱表）。
     *
     * @param orderType 医嘱类型 code
     * @return 表名
     */
    private String resolveRequestTable(String orderType) {
        // 诊嘱补费挂 wkf_service_request；药嘱（含成组）挂 med_medication_request
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(orderType)) {
            return TableNameConstants.WKF_SERVICE_REQUEST;
        }
        return TableNameConstants.MED_MEDICATION_REQUEST;
    }

    /**
     * 绑定耗材实发批号后重定价回调。
     */
    @FunctionalInterface
    public interface BindDevicePriceLineResolver {
        /**
         * 按耗材定义与可选批号解析价目行。
         *
         * @param deviceDefId           耗材定义
         * @param preferredDefinitionId 优先 definitionId；可空
         * @param executionLotNumber    实发批号；可空
         * @return 价目行
         */
        ChargeItemDefinition resolve(
            Long deviceDefId, Long preferredDefinitionId, String executionLotNumber);
    }
}
