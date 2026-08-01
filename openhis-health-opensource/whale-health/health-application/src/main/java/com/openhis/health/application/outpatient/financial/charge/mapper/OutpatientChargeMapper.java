/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.mapper;

import com.openhis.health.application.outpatient.financial.charge.dto.ChargeQueueRowDto;
import com.openhis.health.application.outpatient.financial.charge.dto.PreSettleChargeLinePreviewDto;
import com.openhis.health.application.outpatient.financial.charge.request.ConfirmOutpatientChargeSettlementRequest;
import com.openhis.health.application.outpatient.financial.charge.response.ConfirmOutpatientChargeSettlementResponse;
import com.openhis.health.application.outpatient.financial.charge.response.LoadChargePageInitResponse;
import com.openhis.health.application.outpatient.financial.charge.response.PageOutpatientChargeQueueResponse;
import com.openhis.health.application.outpatient.financial.charge.response.PreSettleOutpatientChargesResponse;
import com.openhis.health.application.outpatient.financial.charge.response.SwitchEncounterChargeItemsAccountResponse;
import com.openhis.health.domain.administration.chargeitem.command.BatchChargeItemIdsCommand;
import com.openhis.health.domain.administration.invoice.command.CreateDraftInvoiceCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.CreatePaymentDraftFromPreSettleCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.SettleOutpatientPaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 门诊收费 Mapper 契约。
 */
public interface OutpatientChargeMapper {
    LoadChargePageInitResponse toLoadChargePageInitResponse(
        List<OptionDto> chargeItemStatusOptions,
        List<OptionDto> paymentChannelOptions);

    List<ChargeQueueRowDto> toChargeQueueRowDtoList(List<OutpatientEncounterContextQueryModel> baseRows, Map<Long, String> flags);

    PageOutpatientChargeQueueResponse toPageOutpatientChargeQueueResponse(Page<?> page, List<ChargeQueueRowDto> records);

    BatchChargeItemIdsCommand toBatchChargeItemIdsCommand(List<Long> chargeItemIds, Long operatorId);

    /**
     * 装配创建支付草稿命令（头字段）；{@code paymentDetails} 由调用方在调用领域服务前补齐。
     *
     * @param encounterId   就诊主键
     * @param patientId     患者主键
     * @param chargeItemIds 收费项主键拼接串
     * @param amount        展示与实收金额（预结算合计）
     * @param operatorId    经办人主键
     * @param contractNo    合同业务号，可空
     * @return 待补全明细的创建草稿命令
     */
    CreatePaymentDraftFromPreSettleCommand toCreatePaymentDraftFromPreSettleCommand(
        Long encounterId,
        Long patientId,
        String chargeItemIds,
        BigDecimal amount,
        Long operatorId,
        String contractNo);

    SettleOutpatientPaymentCommand toSettleOutpatientPaymentCommand(ConfirmOutpatientChargeSettlementRequest request);

    /**
     * 装配创建票据草稿命令。
     *
     * @param payment           支付流水
     * @param paymentId         支付主键
     * @param operatorId        操作人
     * @param invoiceFaceAmount 票面金额（收费项合计结算刻度；含医保已付）
     * @return 票据草稿命令
     */
    CreateDraftInvoiceCommand toCreateDraftInvoiceCommand(
        PaymentReconciliationQueryModel payment,
        Long paymentId,
        Long operatorId,
        BigDecimal invoiceFaceAmount);

    SwitchEncounterChargeItemsAccountResponse toSwitchAccountResponse(boolean success, String messageCode);

    PreSettleOutpatientChargesResponse toPreSettleOutpatientChargesResponse(
        Long paymentDraftId,
        BigDecimal displayAmount,
        List<PreSettleChargeLinePreviewDto> previews);

    ConfirmOutpatientChargeSettlementResponse toConfirmOutpatientChargeSettlementResponse(Long paymentId, Long invoiceId);
}
