/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.outpatient.doctorstation.refundapplication.mapper;

import com.openhis.health.domain.share.constants.DoctorStationRefundApplicationConstants;
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
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.servicerequest.command.CancelServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.command.StopServiceRequestCommand;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * {@link DoctorStationRefundApplicationMapper} 的 Spring 装配实现：字段级搬运，不含业务分支。
 */
@Component
public class DoctorStationRefundApplicationMapperImpl implements DoctorStationRefundApplicationMapper {

    /**
     * IF-DRA-01（Asm）：装配单条可退账单行。
     *
     * @param chargeItem       收费明细实体；调用方保证非空
     * @param payment          支付流水读模型；调用方保证非空
     * @param itemDisplayName  收费项名称摘要；允许 null
     * @return 可退行 DTO；若 chargeItem 为 null 返回 null
     */
    @Override
    public RefundableChargeLineItemDto toRefundableChargeLineItemDto(
        ChargeItem chargeItem,
        PaymentReconciliationQueryModel payment,
        String itemDisplayName
    ) {
        if (chargeItem == null) {
            return null;
        }
        RefundableChargeLineItemDto dto = new RefundableChargeLineItemDto();
        dto.setPaymentId(payment.getId());
        dto.setPaymentBusNo(payment.getPaymentNo());
        if (payment.getStatusEnum() != null) {
            dto.setPaymentStatusEnum(payment.getStatusEnum().getCode());
            dto.setPaymentStatusEnumText(payment.getStatusEnum().getDisplay());
        }
        dto.setChargeItemId(chargeItem.getId());
        dto.setEncounterId(chargeItem.getEncounterId());
        dto.setServiceTable(chargeItem.getServiceTable());
        dto.setServiceId(chargeItem.getServiceId());
        ChargeItemStatus chargeStatus = EnumUtil.fromCode(ChargeItemStatus.class, chargeItem.getStatusEnum());
        if (chargeStatus != null) {
            dto.setChargeItemStatusEnum(chargeStatus.getCode());
            dto.setChargeItemStatusEnumText(chargeStatus.getDisplay());
            dto.setRefundStatusText(chargeStatus.getDisplay());
        } else {
            dto.setChargeItemStatusEnum(chargeItem.getStatusEnum());
            dto.setChargeItemStatusEnumText(null);
            dto.setRefundStatusText(null);
        }
        dto.setItemDisplayName(itemDisplayName);
        dto.setQuantity(chargeItem.getQuantityValue());
        dto.setLineTotalAmount(chargeItem.getTotalPrice());
        dto.setPaymentGroupSortKey(payment.getId());
        return dto;
    }

    /**
     * IF-DRA-01（Asm）：装配列表响应并按支付/收费行排序。
     *
     * @param lines 可退行集合；允许空表
     * @return 响应对象；lines 为 null 时按空表处理
     */
    @Override
    public ListRefundableChargeLinesForEncounterResponse toListRefundableChargeLinesForEncounterResponse(
        List<RefundableChargeLineItemDto> lines
    ) {
        ListRefundableChargeLinesForEncounterResponse response = new ListRefundableChargeLinesForEncounterResponse();
        if (lines == null) {
            response.setLines(Collections.emptyList());
            return response;
        }
        List<RefundableChargeLineItemDto> sorted = new ArrayList<>(lines);
        sorted.sort(
            Comparator.comparing(RefundableChargeLineItemDto::getPaymentId, Comparator.nullsLast(Long::compareTo))
                .thenComparing(RefundableChargeLineItemDto::getChargeItemId, Comparator.nullsLast(Long::compareTo))
        );
        response.setLines(sorted);
        return response;
    }

    /**
     * IF-DRA-02（Asm）：装配耗材门禁响应。
     *
     * @param allowed          是否允许
     * @param rejectReasonCode 拒绝语义码；允许时传 null
     * @return 响应对象
     */
    @Override
    public VerifyConsumableExecutionBeforeRefundResponse toVerifyConsumableExecutionBeforeRefundResponse(
        boolean allowed,
        String rejectReasonCode
    ) {
        VerifyConsumableExecutionBeforeRefundResponse response = new VerifyConsumableExecutionBeforeRefundResponse();
        response.setAllowed(allowed);
        response.setRejectReasonCode(rejectReasonCode);
        return response;
    }

    /**
     * IF-DRA-03（Asm→Cmd）：装配保留链批量落库命令壳（条目由编排侧在 clone* 后写入）。
     *
     * @param operatorId  操作人主键
     * @param anchorItems 再生锚点集合；允许空表（表示无保留行）
     * @return 领域命令；anchorItems 为空时 items 为空列表
     */
    @Override
    public CreateChargeItemsLinkingRegeneratedAnchorsCommand toCreateChargeItemsLinkingRegeneratedAnchorsCommand(
        Long operatorId,
        List<CreateChargeItemsLinkingRegeneratedAnchorsCommand.Item> anchorItems
    ) {
        CreateChargeItemsLinkingRegeneratedAnchorsCommand cmd = new CreateChargeItemsLinkingRegeneratedAnchorsCommand();
        cmd.setOperatorId(operatorId);
        if (anchorItems == null || anchorItems.isEmpty()) {
            cmd.setItems(Collections.emptyList());
        } else {
            cmd.setItems(new ArrayList<>(anchorItems));
        }
        return cmd;
    }

    /**
     * IF-DRA-03（Asm）：装配拟退收费明细批量命令。
     *
     * @param chargeItemIds 拟退收费明细主键集合
     * @param operatorId    操作人主键
     * @return 领域命令
     */
    @Override
    public BatchChargeItemIdsCommand toBatchChargeItemIdsCommand(Collection<Long> chargeItemIds, Long operatorId) {
        BatchChargeItemIdsCommand cmd = new BatchChargeItemIdsCommand();
        cmd.setOperatorId(operatorId);
        List<BatchChargeItemIdsCommand.Item> items = new ArrayList<>();
        if (chargeItemIds != null) {
            for (Long id : chargeItemIds) {
                if (id == null) {
                    continue;
                }
                BatchChargeItemIdsCommand.Item it = new BatchChargeItemIdsCommand.Item();
                it.setChargeItemId(id);
                items.add(it);
            }
        }
        cmd.setItems(items);
        return cmd;
    }

    /**
     * IF-DRA-03（Asm）：装配退药请求命令。
     *
     * @param chargeItem 拟退药品收费行
     * @param operatorId 操作人主键
     * @return 领域命令
     */
    @Override
    public CreateRefundMedicationOrderCommand toCreateRefundMedicationOrderCommand(ChargeItem chargeItem, Long operatorId) {
        CreateRefundMedicationOrderCommand command = new CreateRefundMedicationOrderCommand();
        command.setOperatorId(operatorId);
        command.setOriginRequestId(chargeItem.getServiceId());
        return command;
    }

    /**
     * IF-DRA-03（Asm）：装配重开药品医嘱命令。
     *
     * @param medicationRequestId 源药品申请主键
     * @param operatorId          操作人主键
     * @return 领域命令
     */
    @Override
    public ReopenMedicationOrderCommand toReopenMedicationOrderCommand(Long medicationRequestId, Long operatorId) {
        ReopenMedicationOrderCommand command = new ReopenMedicationOrderCommand();
        command.setOperatorId(operatorId);
        command.setOriginRequestId(medicationRequestId);
        return command;
    }

    /**
     * IF-DRA-03（Asm）：装配待退药发放命令。
     *
     * @param dispense               原发放单
     * @param refundMedicationRequestId 退药申请主键
     * @param operatorId             操作人主键
     * @return 领域命令
     */
    @Override
    public CreatePendingRefundDispenseCommand toCreatePendingRefundDispenseCommand(
        MedicationDispense dispense,
        Long refundMedicationRequestId,
        Long operatorId
    ) {
        CreatePendingRefundDispenseCommand command = new CreatePendingRefundDispenseCommand();
        command.setOperatorId(operatorId);
        command.setOriginDispenseId(dispense.getId());
        command.setRefundRequestId(refundMedicationRequestId);
        command.setRefundQuantity(dispense.getQuantity());
        return command;
    }

    /**
     * IF-DRA-03（Asm）：装配作废药品发放命令（单条）。
     *
     * @param dispenseId               发放主键
     * @param notPerformedReasonEnum   未执行原因码
     * @param operatorId               操作人主键
     * @return 领域命令
     */
    @Override
    public CancelMedicationDispenseCommand toCancelMedicationDispenseCommand(
        Long dispenseId,
        String notPerformedReasonEnum,
        Long operatorId
    ) {
        CancelMedicationDispenseCommand command = new CancelMedicationDispenseCommand();
        command.setOperatorId(operatorId);
        CancelMedicationDispenseCommand.Item item = new CancelMedicationDispenseCommand.Item();
        item.setId(dispenseId);
        item.setNotPerformedReasonEnum(notPerformedReasonEnum);
        command.setItem(item);
        return command;
    }

    /**
     * IF-DRA-03（Asm）：装配退耗材命令（单条，数量取收费行计价数量）。
     *
     * @param chargeItem 拟退耗材收费行
     * @param operatorId 操作人主键
     * @return 领域命令
     */
    @Override
    public CreateDeviceOrderRefundCommand toCreateDeviceOrderRefundCommand(ChargeItem chargeItem, Long operatorId) {
        CreateDeviceOrderRefundCommand command = new CreateDeviceOrderRefundCommand();
        command.setOperatorId(operatorId);
        CreateDeviceOrderRefundCommand.CreateDeviceOrderRefundItemCommand item =
            new CreateDeviceOrderRefundCommand.CreateDeviceOrderRefundItemCommand();
        item.setSourceRequestId(chargeItem.getServiceId());
        BigDecimal qty = chargeItem.getQuantityValue();
        if (qty == null) {
            qty = BigDecimal.ZERO;
        }
        item.setRefundQuantity(qty);
        item.setReason(DoctorStationRefundApplicationConstants.DEVICE_ORDER_REFUND_REASON_OUTPATIENT_REFUND_APP);
        command.setItem(item);
        return command;
    }

    /**
     * IF-DRA-03（Asm）：装配待退耗材发放命令（单条）。
     *
     * @param sourceDispenseId 原发放单主键
     * @param refundQuantity   拟退数量
     * @param operatorId       操作人主键
     * @return 领域命令
     */
    @Override
    public CreatePendingRefundDeviceDispenseCommand toCreatePendingRefundDeviceDispenseCommand(
        Long sourceDispenseId,
        BigDecimal refundQuantity,
        Long operatorId
    ) {
        CreatePendingRefundDeviceDispenseCommand command = new CreatePendingRefundDeviceDispenseCommand();
        command.setOperatorId(operatorId);
        CreatePendingRefundDeviceDispenseCommand.CreatePendingRefundDeviceDispenseItemCommand item =
            new CreatePendingRefundDeviceDispenseCommand.CreatePendingRefundDeviceDispenseItemCommand();
        item.setSourceDispenseId(sourceDispenseId);
        item.setRefundQuantity(refundQuantity);
        command.setItem(item);
        return command;
    }

    /**
     * IF-DRA-03（Asm）：装配作废未发耗材发放命令（单条）。
     *
     * @param dispenseId               发放主键
     * @param notPerformedReasonEnum   未执行原因码
     * @param operatorId               操作人主键
     * @return 领域命令
     */
    @Override
    public CancelUndispensedDeviceDispenseCommand toCancelUndispensedDeviceDispenseCommand(
        Long dispenseId,
        String notPerformedReasonEnum,
        Long operatorId
    ) {
        CancelUndispensedDeviceDispenseCommand command = new CancelUndispensedDeviceDispenseCommand();
        command.setOperatorId(operatorId);
        CancelUndispensedDeviceDispenseCommand.CancelUndispensedDeviceDispenseItemCommand item =
            new CancelUndispensedDeviceDispenseCommand.CancelUndispensedDeviceDispenseItemCommand();
        item.setId(dispenseId);
        item.setNotPerformedReasonEnum(notPerformedReasonEnum);
        command.setItem(item);
        return command;
    }

    /**
     * IF-DRA-03（Asm）：装配取消诊疗服务申请命令（单条）。
     *
     * @param serviceRequestId 服务申请主键
     * @param operatorId       操作人主键
     * @return 领域命令
     */
    @Override
    public CancelServiceRequestCommand toCancelServiceRequestCommand(Long serviceRequestId, Long operatorId) {
        CancelServiceRequestCommand command = new CancelServiceRequestCommand();
        command.setOperatorId(operatorId);
        CancelServiceRequestCommand.CancelServiceRequestItemCommand item = new CancelServiceRequestCommand.CancelServiceRequestItemCommand();
        item.setId(serviceRequestId);
        item.setCancelReason(DoctorStationRefundApplicationConstants.SERVICE_REQUEST_CANCEL_REASON_OUTPATIENT_REFUND_APP);
        command.setItem(item);
        return command;
    }

    /**
     * IF-DRA-03（Asm）：装配停止诊疗服务申请命令（单条）。
     *
     * @param serviceRequestId 服务申请主键
     * @param operatorId       操作人主键
     * @return 领域命令
     */
    @Override
    public StopServiceRequestCommand toStopServiceRequestCommand(Long serviceRequestId, Long operatorId) {
        StopServiceRequestCommand command = new StopServiceRequestCommand();
        command.setOperatorId(operatorId);
        StopServiceRequestCommand.StopServiceRequestItemCommand item = new StopServiceRequestCommand.StopServiceRequestItemCommand();
        item.setId(serviceRequestId);
        item.setStopTime(null);
        item.setReason(DoctorStationRefundApplicationConstants.SERVICE_REQUEST_STOP_REASON_OUTPATIENT_REFUND_APP);
        command.setItem(item);
        return command;
    }

    /**
     * IF-DRA-03（Asm）：装配提交成功响应。
     *
     * @param success                  受理成功
     * @param refundingChargeItemCount 拟退行数
     * @param paymentSummaries         按支付分组的执行衔接摘要
     * @return 响应对象
     */
    @Override
    public SubmitOutpatientRefundApplicationResponse toSubmitOutpatientRefundApplicationResponse(
        boolean success,
        int refundingChargeItemCount,
        List<OutpatientRefundApplicationPaymentSummaryDto> paymentSummaries
    ) {
        SubmitOutpatientRefundApplicationResponse response = new SubmitOutpatientRefundApplicationResponse();
        response.setSuccess(success);
        response.setRefundingChargeItemCount(refundingChargeItemCount);
        if (paymentSummaries == null) {
            response.setPaymentSummaries(Collections.emptyList());
        } else {
            response.setPaymentSummaries(paymentSummaries);
        }
        return response;
    }
}
