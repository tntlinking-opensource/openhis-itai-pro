/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.refund.mapper;

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
import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.RequestOutpatientRefundCommand;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 门诊退费应用层映射器契约（AS-OP-FIN-003）。
 */
public interface OutpatientRefundMapper {

    /**
     * 装配退费页初始化响应。
     *
     * @param chargeItemStatusOptions 收费项状态下拉
     * @param paymentChannelOptions   用户可选支付渠道下拉
     * @return 初始化响应
     */
    LoadOutpatientRefundPageInitResponse toLoadOutpatientRefundPageInitResponse(
        List<OptionDto> chargeItemStatusOptions,
        List<OptionDto> paymentChannelOptions);

    /**
     * 将门诊信息读模型装配为退费队列行。
     *
     * @param baseRows 门诊信息行
     * @param flags 队列标签 encounterId → code
     * @return 队列行列表
     */
    List<OutpatientRefundQueueRowDto> toOutpatientRefundQueueRowDtoList(
        List<OutpatientEncounterContextQueryModel> baseRows,
        Map<Long, String> flags);

    /**
     * 装配退费队列分页响应。
     *
     * @param page 源分页壳
     * @param records 当前页行
     * @return 分页响应
     */
    PageOutpatientRefundQueueResponse toPageOutpatientRefundQueueResponse(
        Page<?> page,
        List<OutpatientRefundQueueRowDto> records);

    /**
     * 装配执行明细响应。
     *
     * @param encounterId 就诊主键
     * @param paymentGroups 支付分组
     * @return 明细响应
     */
    LoadOutpatientRefundExecutionDetailResponse toLoadOutpatientRefundExecutionDetailResponse(
        Long encounterId,
        List<OutpatientRefundPaymentGroupDto> paymentGroups);

    /**
     * 装配可退门禁响应。
     *
     * @param allowed 是否允许
     * @param rejectReasonCode 拒绝码；允许时为 null
     * @return 门禁响应
     */
    VerifyRefundableBeforeExecutionResponse toVerifyRefundableBeforeExecutionResponse(
        boolean allowed,
        String rejectReasonCode);

    /**
     * 装配门诊退费申请命令。
     *
     * @param request 执行退费请求
     * @param originalPayment 原支付读模型
     * @param operatorId 操作人
     * @param chargeItemIdsJoined 收费项 id 拼接串
     * @param refundAmount 渠道应退合计（校验用；主单 display/tendered 取原单患者应付）
     * @param paymentDetails 逆向单明细
     * @return 申请命令
     */
    RequestOutpatientRefundCommand toRequestOutpatientRefundCommand(
        ExecuteOutpatientBillRefundByPaymentRequest request,
        PaymentReconciliationQueryModel originalPayment,
        Long operatorId,
        String chargeItemIdsJoined,
        BigDecimal refundAmount,
        List<PaymentDetailItemCommand> paymentDetails);

    /**
     * 装配逆向单完成退费命令。
     *
     * @param reversePaymentId 逆向支付主键
     * @param outcomeEnum 结果码
     * @param billDate 账单日
     * @return 命令
     */
    CompleteOutpatientRefundCommand toCompleteOutpatientRefundCommand(
        Long reversePaymentId,
        String outcomeEnum,
        LocalDateTime billDate);

    /**
     * 装配收费明细批量收口命令。
     *
     * @param chargeItemIds 收口 id 列表
     * @param operatorId 操作人
     * @return 批量命令
     */
    BatchChargeItemIdsCommand toBatchChargeItemIdsCommand(List<Long> chargeItemIds, Long operatorId);

    /**
     * 装配退费执行成功响应。
     *
     * @param reversePaymentId 逆向单主键
     * @param originalPaymentId 原单主键
     * @param halfRefundExecuted 是否半退再结算
     * @return 响应
     */
    ExecuteOutpatientBillRefundByPaymentResponse toExecuteOutpatientBillRefundByPaymentResponse(
        Long reversePaymentId,
        Long originalPaymentId,
        boolean halfRefundExecuted);

    /**
     * 装配保留行账户切换响应。
     *
     * @param success 是否成功
     * @return 响应
     */
    SwitchPreservedChargeItemsAccountResponse toSwitchPreservedChargeItemsAccountResponse(boolean success);

    /**
     * 将渠道实退明细转为领域支付明细命令项。
     *
     * @param submits 提交明细
     * @return 领域明细列表
     */
    List<PaymentDetailItemCommand> toPaymentDetailItemCommands(List<RefundPaymentDetailSubmitDto> submits);

    /**
     * 装配退费明细行 DTO。
     *
     * @param chargeItem 收费明细
     * @param paymentId 支付锚点
     * @param itemDisplayName 展示名
     * @return 行 DTO
     */
    OutpatientRefundLineItemDto toOutpatientRefundLineItemDto(
        ChargeItem chargeItem,
        Long paymentId,
        String itemDisplayName);

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
    OutpatientRefundPaymentGroupDto toOutpatientRefundPaymentGroupDto(
        PaymentReconciliationQueryModel payment,
        List<OutpatientRefundLineItemDto> refundingLines,
        List<OutpatientRefundLineItemDto> preservedLines,
        BigDecimal refundableAmount,
        boolean halfRefundOnPayment,
        String collectorName);
}
