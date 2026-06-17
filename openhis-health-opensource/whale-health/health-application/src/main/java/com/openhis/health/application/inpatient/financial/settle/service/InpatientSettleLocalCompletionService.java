/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.financial.settle.service;

import com.openhis.health.application.inpatient.financial.prepay.mapper.InpatientPrepayMapper;
import com.openhis.health.application.inpatient.financial.settle.mapper.InpatientSettleMapper;
import com.openhis.health.application.insurance.coordination.dto.InpatientIpSettleCoordinationSnapshot;
import com.openhis.health.module.common.assembler.inpatient.InpatientDisplayAccountBalanceAssembler;
import com.openhis.health.domain.administration.account.command.AdjustAdvanceDepositBalanceCommand;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountDomainService;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.chargeitem.command.BatchChargeItemIdsCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.command.CompleteInpatientFinancialSettlementCommand;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.invoice.command.CreateDraftInvoiceCommand;
import com.openhis.health.domain.administration.invoice.service.InvoiceDomainService;
import com.openhis.health.domain.financial.paymentreconciliation.command.RefundAdvanceDepositCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.SettleInpatientPaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.constants.InpatientSettleConstants;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 住院结算协同单本地落账服务（2304 平台步骤成功后执行）。
 *
 * <p>职责：支付正式落账、收费项结算、发票草稿、出院全结时预交金多退与就诊财务全结；
 * 由 {@code IpSettleCoordinationExecutor} 在平台确认后回调。</p>
 */
@Service
@RequiredArgsConstructor
public class InpatientSettleLocalCompletionService {

    private final PaymentReconciliationDomainService paymentReconciliationDomainService;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final ChargeItemDomainService chargeItemDomainService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final InvoiceDomainService invoiceDomainService;
    private final EncounterDomainService encounterDomainService;
    private final AccountQueryService accountQueryService;
    private final AccountDomainService accountDomainService;
    private final InpatientSettleMapper inpatientSettleMapper;
    private final InpatientPrepayMapper inpatientPrepayMapper;

    /** 住院协同本地落账结果。 */
    @Data
    public static class LocalCompletionResult {
        /** 正式结算后的支付单主键。 */
        private Long paymentId;
        /** 支付完成后创建的发票草稿主键。 */
        private Long invoiceDraftId;
        /** 出院全结时预交金盈余退款单 ID；无退款时为 null。 */
        private Long advanceRefundPaymentId;
        /** 是否按出院全结路径处理（含预交金退余与就诊财务全结）。 */
        private boolean dischargeFullSettle;
    }

    /**
     * 从协同快照执行住院本地落账（2304 成功后）。
     *
     * @param snapshot  预结算阶段持久化的协同快照
     * @param ybSetlId  医保结算 ID（写入支付单 yb_settle_ids）
     * @return 落账结果
     * @throws BusinessException 快照缺失或账户/登记不存在时抛出
     */
    public LocalCompletionResult completeFromSnapshot(
        InpatientIpSettleCoordinationSnapshot snapshot,
        String ybSetlId) {
        // Gate：协同本地落账须具备快照与支付草稿 ID。
        if (snapshot == null || snapshot.getPaymentDraftId() == null) {
            throw new BusinessException(InpatientSettleConstants.EX_IP_STL_109, "协同单本地落账上下文缺失");
        }
        // Cmd：正式结算支付草稿并回写医保结算 ID。
        SettleInpatientPaymentCommand settleCommand = new SettleInpatientPaymentCommand();
        settleCommand.setPaymentId(snapshot.getPaymentDraftId());
        settleCommand.setOutcomeEnum(PaymentStatus.SUCCESS.getCode());
        settleCommand.setYbSettleIds(ybSetlId);
        LocalDateTime billDate = snapshot.getBillDate();
        if (billDate == null) {
            billDate = LocalDateTime.now();
        }
        settleCommand.setBillDate(billDate);
        Long paymentId = paymentReconciliationDomainService.settleInpatientPayment(settleCommand);
        // Cmd：批量推进收费项至已结算态。
        BatchChargeItemIdsCommand settleChargeCmd =
            inpatientSettleMapper.toBatchChargeItemIdsCommand(snapshot.getChargeItemIds(), snapshot.getOperatorId());
        chargeItemDomainService.settleInpatientChargeItems(settleChargeCmd);
        // Pre：回查已结算支付单以创建发票草稿。
        PaymentReconciliationQueryModel settledPayment =
            paymentReconciliationQueryService.mustGetPayment(paymentId);
        CreateDraftInvoiceCommand invoiceCommand =
            inpatientSettleMapper.toCreateDraftInvoiceCommand(settledPayment, paymentId);
        invoiceCommand.setOperatorId(snapshot.getOperatorId());
        Long invoiceDraftId = invoiceDomainService.createDraftInvoiceAfterPayment(invoiceCommand);
        boolean dischargeFullSettle = Boolean.TRUE.equals(snapshot.getDischargeFullSettle());
        Long advanceRefundPaymentId = null;
        // Cmd：出院全结时退预交金盈余并完成就诊财务全结。
        if (dischargeFullSettle) {
            ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
            loadCmd.setIds(snapshot.getChargeItemIds());
            List<ChargeItem> chargeItems = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
            BigDecimal batchTotal = this.sumChargeItemAmount(chargeItems);
            BigDecimal settledBefore = snapshot.getSettledBeforeCurrentBatch();
            if (settledBefore == null) {
                settledBefore = BigDecimal.ZERO;
            }
            advanceRefundPaymentId = this.refundDischargeAdvanceDepositSurplus(
                snapshot.getEncounterId(),
                snapshot.getPatientId(),
                snapshot.getOperatorId(),
                settledBefore,
                batchTotal);
            CompleteInpatientFinancialSettlementCommand completeCmd =
                inpatientSettleMapper.toCompleteInpatientFinancialSettlementCommand(
                    snapshot.getEncounterId(),
                    billDate,
                    snapshot.getOperatorId());
            encounterDomainService.completeInpatientFinancialSettlement(completeCmd);
        }
        // Asm：封装落账结果供协同单步骤回写。
        LocalCompletionResult result = new LocalCompletionResult();
        result.setPaymentId(paymentId);
        result.setInvoiceDraftId(invoiceDraftId);
        result.setAdvanceRefundPaymentId(advanceRefundPaymentId);
        result.setDischargeFullSettle(dischargeFullSettle);
        return result;
    }

    /**
     * 出院全结时退还预交金净值超出已结算占用的盈余部分。
     *
     * @param encounterId               住院就诊 ID
     * @param patientId                 患者 ID
     * @param operatorId                操作人
     * @param settledBeforeCurrentBatch 本批结算前已结算占用
     * @param currentBatchSettledTotal  本批结算金额合计
     * @return 退款支付单 ID；无需退款时返回 null
     * @throws BusinessException 登记账户不存在时抛出
     */
    private Long refundDischargeAdvanceDepositSurplus(
        Long encounterId,
        Long patientId,
        Long operatorId,
        BigDecimal settledBeforeCurrentBatch,
        BigDecimal currentBatchSettledTotal) {
        Account account = accountQueryService.getRegistrationAccount(patientId, encounterId);
        if (account == null || account.getId() == null) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_103,
                "请先完成住院登记");
        }
        BigDecimal prepayNet = InpatientDisplayAccountBalanceAssembler.resolvePersonalCashPrepayNet(account);
        BigDecimal settledOccupancy = settledBeforeCurrentBatch;
        if (settledOccupancy == null) {
            settledOccupancy = BigDecimal.ZERO;
        }
        if (currentBatchSettledTotal != null) {
            settledOccupancy = settledOccupancy.add(currentBatchSettledTotal);
        }
        BigDecimal refundAmount = prepayNet.subtract(settledOccupancy)
            .setScale(InpatientSettleConstants.SETTLE_AMOUNT_COMPARE_SCALE, RoundingMode.HALF_UP);
        // 盈余≤0 时不发起退款。
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        RefundAdvanceDepositCommand refundCommand = inpatientSettleMapper.toDischargeDepositRefundCommand(
            encounterId,
            patientId,
            account.getId(),
            refundAmount,
            operatorId);
        Long refundPaymentId = paymentReconciliationDomainService.refundAdvanceDeposit(refundCommand);
        SettleInpatientPaymentCommand refundSettleCommand =
            inpatientPrepayMapper.toSettleInpatientPaymentCommand(refundPaymentId);
        paymentReconciliationDomainService.settleInpatientPayment(refundSettleCommand);
        AdjustAdvanceDepositBalanceCommand balanceCmd = inpatientPrepayMapper.toRefundBalanceAdjustCommand(
            patientId,
            encounterId,
            refundAmount,
            operatorId);
        accountDomainService.adjustAdvanceDepositBalance(balanceCmd);
        return refundPaymentId;
    }

    /**
     * 汇总收费项 total_price。
     *
     * @param chargeItems 收费项列表
     * @return 金额合计；空列表返回 ZERO
     */
    private BigDecimal sumChargeItemAmount(List<ChargeItem> chargeItems) {
        BigDecimal sum = BigDecimal.ZERO;
        if (chargeItems == null) {
            return sum;
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getTotalPrice() == null) {
                continue;
            }
            sum = sum.add(chargeItem.getTotalPrice());
        }
        return sum;
    }
}
