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

package com.openhis.health.application.outpatient.financial.refund.mapper;

import com.openhis.health.module.common.pagination.PagedResultBuilder;
import com.openhis.health.domain.share.constants.OutpatientRefundQueueTab;
import com.openhis.health.application.outpatient.financial.refund.dto.OutpatientRefundLineItemDto;
import com.openhis.health.application.outpatient.financial.refund.dto.OutpatientRefundPaymentGroupDto;
import com.openhis.health.application.outpatient.financial.refund.dto.OutpatientRefundQueueRowDto;
import com.openhis.health.application.outpatient.financial.refund.dto.RefundPaymentDetailSubmitDto;
import com.openhis.health.application.outpatient.financial.refund.request.ExecuteOutpatientBillRefundByPaymentRequest;
import com.openhis.health.application.outpatient.financial.refund.response.ExecuteOutpatientBillRefundByPaymentResponse;
import com.openhis.health.application.outpatient.financial.refund.response.LoadOutpatientRefundExecutionDetailResponse;
import com.openhis.health.application.outpatient.financial.refund.response.LoadOutpatientRefundPageInitResponse;
import com.openhis.health.application.outpatient.financial.refund.response.PageOutpatientRefundQueueResponse;
import com.openhis.health.application.outpatient.financial.refund.response.SwitchPreservedChargeItemsAccountResponse;
import com.openhis.health.application.outpatient.financial.refund.response.VerifyRefundableBeforeExecutionResponse;
import com.openhis.health.domain.administration.chargeitem.command.BatchChargeItemIdsCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.financial.paymentreconciliation.command.CompleteOutpatientRefundCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.FinalizeOriginalOutpatientPaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.RequestOutpatientRefundCommand;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.share.enums.PaymentType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link OutpatientRefundMapper} 实现：门诊退费应用层字段映射（AS-OP-FIN-003）。
 *
 * <ul>
 *   <li>分页响应经 {@link PagedResultBuilder} 装配，页码 1-based（B.10.11）。</li>
 *   <li>枚举展示经 {@link EnumUtil} 解析 {@code getCode()} → {@code getDisplay()}（B.10.8.1）。</li>
 * </ul>
 */
@Component
public class OutpatientRefundMapperImpl implements OutpatientRefundMapper {

    /**
     * 装配退费页初始化响应。
     *
     * @param chargeItemStatusOptions 收费项状态下拉
     * @param paymentChannelOptions   用户可选支付渠道下拉
     * @return 初始化响应
     */
    @Override
    public LoadOutpatientRefundPageInitResponse toLoadOutpatientRefundPageInitResponse(
        List<OptionDto> chargeItemStatusOptions,
        List<OptionDto> paymentChannelOptions) {
        LoadOutpatientRefundPageInitResponse response = new LoadOutpatientRefundPageInitResponse();
        response.setChargeItemStatusOptions(chargeItemStatusOptions);
        response.setPaymentChannelOptions(paymentChannelOptions);
        return response;
    }

    /**
     * 将门诊信息读模型装配为退费队列行。
     *
     * @param baseRows 门诊信息行
     * @param flags 队列标签 encounterId → code
     * @return 队列行列表
     */
    @Override
    public List<OutpatientRefundQueueRowDto> toOutpatientRefundQueueRowDtoList(
        List<OutpatientEncounterContextQueryModel> baseRows,
        Map<Long, String> flags) {
        List<OutpatientRefundQueueRowDto> rows = new ArrayList<>();
        if (baseRows == null) {
            return rows;
        }
        for (OutpatientEncounterContextQueryModel baseRow : baseRows) {
            OutpatientRefundQueueRowDto row = new OutpatientRefundQueueRowDto();
            row.setEncounterId(baseRow.getEncounterId());
            row.setEncounterBusNo(baseRow.getEncounterBusNo());
            row.setPatientId(baseRow.getPatientId());
            row.setPatientName(baseRow.getPatientName());
            row.setGenderCode(baseRow.getGenderEnum());
            AdministrativeGender gender = EnumUtil.fromCode(AdministrativeGender.class, baseRow.getGenderEnum());
            if (gender != null) {
                row.setGenderEnumText(gender.getDisplay());
            }
            row.setAgeText(baseRow.getAgeText());
            row.setOrganizationName(baseRow.getOrganizationName());
            row.setDoctorName(baseRow.getDoctorName());
            row.setStartTime(baseRow.getStartTime());
            row.setTotalAmount(baseRow.getTotalAmount());
            row.setPaidAmount(baseRow.getPaidAmount());
            row.setChargeItemStatusSummary(ChargeItemStatus.REFUNDING.getDisplay());
            if (flags != null && baseRow.getEncounterId() != null) {
                row.setQueueLabelCode(flags.get(baseRow.getEncounterId()));
            }
            if (row.getQueueLabelCode() == null) {
                row.setQueueLabelCode(OutpatientRefundQueueTab.PENDING);
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 装配退费队列分页响应。
     *
     * @param page 源分页壳
     * @param records 当前页行
     * @return 分页响应
     */
    @Override
    public PageOutpatientRefundQueueResponse toPageOutpatientRefundQueueResponse(Page<?> page, List<OutpatientRefundQueueRowDto> records) {
        return PagedResultBuilder.build(new PageOutpatientRefundQueueResponse(), records, page);
    }

    /**
     * 装配执行明细响应。
     *
     * @param encounterId 就诊主键
     * @param paymentGroups 支付分组
     * @return 明细响应
     */
    @Override
    public LoadOutpatientRefundExecutionDetailResponse toLoadOutpatientRefundExecutionDetailResponse(
        Long encounterId,
        List<OutpatientRefundPaymentGroupDto> paymentGroups) {
        LoadOutpatientRefundExecutionDetailResponse response = new LoadOutpatientRefundExecutionDetailResponse();
        response.setEncounterId(encounterId);
        response.setPaymentGroups(paymentGroups);
        return response;
    }

    /**
     * 装配可退门禁响应。
     *
     * @param allowed 是否允许
     * @param rejectReasonCode 拒绝码；允许时为 null
     * @return 门禁响应
     */
    @Override
    public VerifyRefundableBeforeExecutionResponse toVerifyRefundableBeforeExecutionResponse(
        boolean allowed,
        String rejectReasonCode) {
        VerifyRefundableBeforeExecutionResponse response = new VerifyRefundableBeforeExecutionResponse();
        response.setAllowed(allowed);
        response.setRejectReasonCode(rejectReasonCode);
        return response;
    }

    /**
     * 装配门诊退费申请命令。
     *
     * @param request 执行退费请求
     * @param originalPayment 原支付读模型
     * @param operatorId 操作人
     * @param chargeItemIdsJoined 收费项 id 拼接串
     * @param refundAmount 应退金额
     * @param paymentDetails 逆向单明细
     * @return 申请命令
     */
    @Override
    public RequestOutpatientRefundCommand toRequestOutpatientRefundCommand(
        ExecuteOutpatientBillRefundByPaymentRequest request,
        PaymentReconciliationQueryModel originalPayment,
        Long operatorId,
        String chargeItemIdsJoined,
        BigDecimal refundAmount,
        List<PaymentDetailItemCommand> paymentDetails) {
        RequestOutpatientRefundCommand command = new RequestOutpatientRefundCommand();
        command.setRelationId(originalPayment.getId());
        command.setEncounterId(request.getEncounterId());
        command.setPatientId(originalPayment.getPatientId());
        command.setEntererId(operatorId);
        command.setKindEnum(originalPayment.getKindEnum());
        command.setPaymentEnum(PaymentType.UN_PAY.getCode());
        command.setDisplayAmount(refundAmount);
        command.setTenderedAmount(refundAmount);
        command.setChargeItemIds(chargeItemIdsJoined);
        command.setPaymentDetails(paymentDetails);
        return command;
    }

    /**
     * 装配原单静默收口命令。
     *
     * @param originalPaymentId 原支付主键
     * @param outcomeEnum 结果码
     * @param billDate 账单日
     * @return 命令
     */
    @Override
    public FinalizeOriginalOutpatientPaymentCommand toFinalizeOriginalOutpatientPaymentCommand(
        Long originalPaymentId,
        String outcomeEnum,
        LocalDateTime billDate) {
        FinalizeOriginalOutpatientPaymentCommand command = new FinalizeOriginalOutpatientPaymentCommand();
        command.setOriginalPaymentId(originalPaymentId);
        command.setOutcomeEnum(outcomeEnum);
        command.setBillDate(billDate);
        return command;
    }

    /**
     * 装配逆向单完成退费命令。
     *
     * @param reversePaymentId 逆向支付主键
     * @param outcomeEnum 结果码
     * @param billDate 账单日
     * @return 命令
     */
    @Override
    public CompleteOutpatientRefundCommand toCompleteOutpatientRefundCommand(
        Long reversePaymentId,
        String outcomeEnum,
        LocalDateTime billDate) {
        CompleteOutpatientRefundCommand command = new CompleteOutpatientRefundCommand();
        command.setPaymentId(reversePaymentId);
        command.setOutcomeEnum(outcomeEnum);
        command.setBillDate(billDate);
        return command;
    }

    /**
     * 装配收费明细批量收口命令。
     *
     * @param chargeItemIds 收口 id 列表
     * @param operatorId 操作人
     * @return 批量命令
     */
    @Override
    public BatchChargeItemIdsCommand toBatchChargeItemIdsCommand(List<Long> chargeItemIds, Long operatorId) {
        BatchChargeItemIdsCommand command = new BatchChargeItemIdsCommand();
        command.setOperatorId(operatorId);
        List<BatchChargeItemIdsCommand.Item> items = new ArrayList<>();
        for (Long chargeItemId : chargeItemIds) {
            BatchChargeItemIdsCommand.Item item = new BatchChargeItemIdsCommand.Item();
            item.setChargeItemId(chargeItemId);
            items.add(item);
        }
        command.setItems(items);
        return command;
    }

    /**
     * 装配退费执行成功响应。
     *
     * @param reversePaymentId 逆向单主键
     * @param originalPaymentId 原单主键
     * @param halfRefundExecuted 是否半退再结算
     * @return 响应
     */
    @Override
    public ExecuteOutpatientBillRefundByPaymentResponse toExecuteOutpatientBillRefundByPaymentResponse(
        Long reversePaymentId,
        Long originalPaymentId,
        boolean halfRefundExecuted) {
        ExecuteOutpatientBillRefundByPaymentResponse response = new ExecuteOutpatientBillRefundByPaymentResponse();
        response.setSuccess(true);
        response.setReversePaymentId(reversePaymentId);
        response.setOriginalPaymentId(originalPaymentId);
        response.setHalfRefundExecuted(halfRefundExecuted);
        response.setInsuranceRevoked(true);
        return response;
    }

    /**
     * 装配保留行账户切换响应。
     *
     * @param success 是否成功
     * @return 响应
     */
    @Override
    public SwitchPreservedChargeItemsAccountResponse toSwitchPreservedChargeItemsAccountResponse(boolean success) {
        SwitchPreservedChargeItemsAccountResponse response = new SwitchPreservedChargeItemsAccountResponse();
        response.setSuccess(success);
        return response;
    }

    /**
     * 将渠道实退明细转为领域支付明细命令项。
     *
     * @param submits 提交明细
     * @return 领域明细列表
     */
    @Override
    public List<PaymentDetailItemCommand> toPaymentDetailItemCommands(List<RefundPaymentDetailSubmitDto> submits) {
        List<PaymentDetailItemCommand> details = new ArrayList<>();
        if (submits == null) {
            return details;
        }
        for (RefundPaymentDetailSubmitDto submit : submits) {
            PaymentDetailItemCommand detail = new PaymentDetailItemCommand();
            detail.setPayEnum(submit.getPayChannelCode());
            detail.setAmount(submit.getAmount());
            detail.setChargeAmount(submit.getAmount());
            details.add(detail);
        }
        return details;
    }

    /**
     * 装配退费明细行 DTO。
     *
     * @param chargeItem 收费明细
     * @param paymentId 支付锚点
     * @param itemDisplayName 展示名
     * @return 行 DTO
     */
    @Override
    public OutpatientRefundLineItemDto toOutpatientRefundLineItemDto(
        ChargeItem chargeItem,
        Long paymentId,
        String itemDisplayName) {
        if (chargeItem == null) {
            return null;
        }
        OutpatientRefundLineItemDto dto = new OutpatientRefundLineItemDto();
        dto.setChargeItemId(chargeItem.getId());
        dto.setPaymentId(paymentId);
        dto.setChargeItemStatusEnum(chargeItem.getStatusEnum());
        ChargeItemStatus status = EnumUtil.fromCode(ChargeItemStatus.class, chargeItem.getStatusEnum());
        if (status != null) {
            dto.setChargeItemStatusEnumText(status.getDisplay());
        } else {
            dto.setChargeItemStatusEnumText(chargeItem.getStatusEnum());
        }
        dto.setItemDisplayName(itemDisplayName);
        dto.setLineTotalAmount(chargeItem.getTotalPrice());
        return dto;
    }

    /**
     * 装配按支付分组 DTO。
     *
     * @param payment 支付读模型
     * @param refundingLines 拟退行
     * @param preservedLines 保留行
     * @param refundableAmount 应退金额
     * @param halfRefundOnPayment 是否半退
     * @return 分组 DTO
     */
    @Override
    public OutpatientRefundPaymentGroupDto toOutpatientRefundPaymentGroupDto(
        PaymentReconciliationQueryModel payment,
        List<OutpatientRefundLineItemDto> refundingLines,
        List<OutpatientRefundLineItemDto> preservedLines,
        BigDecimal refundableAmount,
        boolean halfRefundOnPayment,
        String collectorName) {
        OutpatientRefundPaymentGroupDto group = new OutpatientRefundPaymentGroupDto();
        group.setPaymentId(payment.getId());
        group.setPaymentBusNo(payment.getPaymentNo());
        PaymentStatus paymentStatus = payment.getStatusEnum();
        if (paymentStatus != null) {
            group.setPaymentStatusEnum(paymentStatus.getCode());
            group.setPaymentStatusEnumText(paymentStatus.getDisplay());
        } else {
            group.setPaymentStatusEnum(null);
            group.setPaymentStatusEnumText(null);
        }
        group.setEntererUserId(payment.getEntererId());
        group.setCollectorName(collectorName);
        group.setHalfRefundOnPayment(halfRefundOnPayment);
        group.setRefundingLines(refundingLines);
        group.setPreservedLines(preservedLines);
        group.setRefundableAmount(refundableAmount);
        return group;
    }
}
