/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.refundapplication.mapper;

import com.openhis.health.application.outpatient.doctorstation.refundapplication.dto.OutpatientRefundApplicationPaymentSummaryDto;
import com.openhis.health.application.outpatient.doctorstation.refundapplication.dto.RefundableChargeLineItemDto;
import com.openhis.health.application.outpatient.doctorstation.refundapplication.response.ListRefundableChargeLinesForEncounterResponse;
import com.openhis.health.application.outpatient.doctorstation.refundapplication.response.SubmitOutpatientRefundApplicationResponse;
import com.openhis.health.application.outpatient.doctorstation.refundapplication.response.VerifyConsumableExecutionBeforeRefundResponse;
import com.openhis.health.domain.administration.chargeitem.command.BatchChargeItemIdsCommand;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemsLinkingRegeneratedAnchorsCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.medication.medicationdispense.command.CancelMedicationDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.command.CreatePendingRefundDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationrequest.command.CreateRefundMedicationOrderCommand;
import com.openhis.health.domain.medication.medicationrequest.command.ReopenMedicationOrderCommand;
import com.openhis.health.domain.workflow.devicedispense.command.CancelUndispensedDeviceDispenseCommand;
import com.openhis.health.domain.workflow.devicedispense.command.CreatePendingRefundDeviceDispenseCommand;
import com.openhis.health.domain.workflow.devicerequest.command.CreateDeviceOrderRefundCommand;
import com.openhis.health.domain.workflow.servicerequest.command.CreateServiceOrderRefundCommand;
import com.openhis.health.domain.workflow.servicerequest.command.StopServiceRequestCommand;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * 门诊医生站「申请退费」应用层装配：领域实体/查询模型与对外 DTO、领域写命令之间的字段级映射。
 *
 * <p>禁止承载业务流程判断；互斥分支与门禁在 {@code DoctorStationRefundApplicationAppService} 内完成。</p>
 */
public interface DoctorStationRefundApplicationMapper {

    /**
     * IF-DRA-01（Asm）：装配单条可退账单行。
     *
     * @param chargeItem       收费明细实体；调用方保证非空
     * @param payment          支付流水读模型；调用方保证非空
     * @param itemDisplayName  收费项名称摘要（与门诊收费 {@code itemName} 同口径，由编排侧批量解析）
     * @return 可退行 DTO；若 chargeItem 为 null 返回 null
     */
    RefundableChargeLineItemDto toRefundableChargeLineItemDto(
        ChargeItem chargeItem,
        PaymentReconciliationQueryModel payment,
        String itemDisplayName
    );

    /**
     * IF-DRA-01（Asm）：装配列表响应并按支付/收费行排序。
     *
     * @param lines 可退行集合；允许空表
     * @return 响应对象；lines 为 null 时按空表处理
     */
    ListRefundableChargeLinesForEncounterResponse toListRefundableChargeLinesForEncounterResponse(List<RefundableChargeLineItemDto> lines);

    /**
     * IF-DRA-02（Asm）：装配耗材门禁响应。
     *
     * @param allowed          是否允许
     * @param rejectReasonCode 拒绝语义码；允许时传 null
     * @return 响应对象
     */
    VerifyConsumableExecutionBeforeRefundResponse toVerifyConsumableExecutionBeforeRefundResponse(
        boolean allowed,
        String rejectReasonCode
    );

    /**
     * IF-DRA-03（Asm→Cmd）：装配保留链批量落库命令壳（条目由编排侧在 clone* 后写入）。
     *
     * @param operatorId  操作人主键
     * @param anchorItems 再生锚点集合；允许空表（表示无保留行）
     * @return 领域命令；anchorItems 为空时 items 为空列表
     */
    CreateChargeItemsLinkingRegeneratedAnchorsCommand toCreateChargeItemsLinkingRegeneratedAnchorsCommand(
        Long operatorId,
        List<CreateChargeItemsLinkingRegeneratedAnchorsCommand.Item> anchorItems
    );

    /**
     * IF-DRA-03（Asm）：装配拟退收费明细批量命令。
     *
     * @param chargeItemIds 拟退收费明细主键集合
     * @param operatorId    操作人主键
     * @return 领域命令
     */
    BatchChargeItemIdsCommand toBatchChargeItemIdsCommand(Collection<Long> chargeItemIds, Long operatorId);

    /**
     * IF-DRA-03（Asm）：装配退药请求命令。
     *
     * @param chargeItem 拟退药品收费行
     * @param operatorId 操作人主键
     * @return 领域命令
     */
    CreateRefundMedicationOrderCommand toCreateRefundMedicationOrderCommand(ChargeItem chargeItem, Long operatorId);

    /**
     * IF-DRA-03（Asm）：装配重开药品医嘱命令。
     *
     * @param medicationRequestId 源药品申请主键
     * @param operatorId          操作人主键
     * @return 领域命令
     */
    ReopenMedicationOrderCommand toReopenMedicationOrderCommand(Long medicationRequestId, Long operatorId);

    /**
     * IF-DRA-03（Asm）：装配待退药发放命令。
     *
     * @param dispense               原发放单
     * @param refundMedicationRequestId 退药申请主键
     * @param operatorId             操作人主键
     * @return 领域命令
     */
    CreatePendingRefundDispenseCommand toCreatePendingRefundDispenseCommand(
        MedicationDispense dispense,
        Long refundMedicationRequestId,
        Long operatorId
    );

    /**
     * IF-DRA-03（Asm）：装配作废药品发放命令（单条）。
     *
     * @param dispenseId               发放主键
     * @param notPerformedReasonEnum   未执行原因码
     * @param operatorId               操作人主键
     * @return 领域命令
     */
    CancelMedicationDispenseCommand toCancelMedicationDispenseCommand(
        Long dispenseId,
        String notPerformedReasonEnum,
        Long operatorId
    );

    /**
     * IF-DRA-03（Asm）：装配退耗材命令（单条，数量取收费行计价数量）。
     *
     * @param chargeItem 拟退耗材收费行
     * @param operatorId 操作人主键
     * @return 领域命令
     */
    CreateDeviceOrderRefundCommand toCreateDeviceOrderRefundCommand(ChargeItem chargeItem, Long operatorId);

    /**
     * IF-DRA-03（Asm）：装配待退耗材发放命令（单条）。
     *
     * @param sourceDispenseId      原发放单主键
     * @param refundQuantity        拟退数量
     * @param operatorId            操作人主键
     * @param refundDeviceRequestId 负向耗材申请主键（门诊退费路径必传）
     * @return 领域命令
     */
    CreatePendingRefundDeviceDispenseCommand toCreatePendingRefundDeviceDispenseCommand(
        Long sourceDispenseId,
        BigDecimal refundQuantity,
        Long operatorId,
        Long refundDeviceRequestId
    );

    /**
     * IF-DRA-03（Asm）：装配作废未发耗材发放命令（单条）。
     *
     * @param dispenseId               发放主键
     * @param notPerformedReasonEnum   未执行原因码
     * @param operatorId               操作人主键
     * @return 领域命令
     */
    CancelUndispensedDeviceDispenseCommand toCancelUndispensedDeviceDispenseCommand(
        Long dispenseId,
        String notPerformedReasonEnum,
        Long operatorId
    );

    /**
     * IF-DRA-03（Asm）：装配退诊疗负向申请命令（单条，数量取收费行计价数量）。
     *
     * @param chargeItem 拟退诊疗收费行
     * @param operatorId 操作人主键
     * @return 领域命令
     */
    CreateServiceOrderRefundCommand toCreateServiceOrderRefundCommand(ChargeItem chargeItem, Long operatorId);

    /**
     * IF-DRA-03（Asm）：装配停止诊疗服务申请命令（单条）。
     *
     * @param serviceRequestId 服务申请主键
     * @param operatorId       操作人主键
     * @return 领域命令
     */
    StopServiceRequestCommand toStopServiceRequestCommand(Long serviceRequestId, Long operatorId);

    /**
     * IF-DRA-03（Asm）：装配提交成功响应。
     *
     * @param success                    受理成功
     * @param refundingChargeItemCount   拟退行数
     * @param paymentSummaries           按支付分组的执行衔接摘要
     * @return 响应对象
     */
    SubmitOutpatientRefundApplicationResponse toSubmitOutpatientRefundApplicationResponse(
        boolean success,
        int refundingChargeItemCount,
        List<OutpatientRefundApplicationPaymentSummaryDto> paymentSummaries
    );
}
