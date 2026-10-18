/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.bridge.settle.support;

import com.zxplat.domain.administration.account.entity.Account;
import com.zxplat.domain.administration.account.service.AccountQueryService;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.zxplat.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.zxplat.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.PaymentChannel;
import com.zxplat.domain.share.enums.PaymentStatus;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.plugin.wi.bridge.settle.workflow.WorkInjuryOutpatientSettleOrchestrator;
import com.zxplat.plugin.wi.domain.settle.command.RefreshWorkInjuryPreSettleAmountsCommand;
import com.zxplat.plugin.wi.domain.settle.result.RefreshWorkInjuryPreSettleAmountsResult;
import com.zxplat.plugin.wi.domain.settle.result.WorkInjuryPreSettleSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 2206 invoke 完成后刷新收银草稿应付金额（从 health-app 迁出）。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.modules.work-injury.enabled", havingValue = "true", matchIfMissing = true)
public class WorkInjuryPreSettleRefreshSupport {

    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final PaymentReconciliationDomainService paymentReconciliationDomainService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final AccountQueryService accountQueryService;
    private final WorkInjuryOutpatientSettleOrchestrator workInjuryOutpatientSettleOrchestrator;

    public RefreshWorkInjuryPreSettleAmountsResult refresh(RefreshWorkInjuryPreSettleAmountsCommand command) {
        if (command == null || command.getPaymentDraftId() == null || command.getEncounterId() == null) {
            throw new BusinessException(
                    ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                    "paymentDraftId 与 encounterId 不能为空");
        }
        PaymentReconciliationQueryModel payment =
                paymentReconciliationQueryService.mustGetPayment(command.getPaymentDraftId());
        if (!command.getEncounterId().equals(payment.getEncounterId())) {
            throw new BusinessException(
                    ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                    "支付草稿与就诊不一致");
        }
        WorkInjuryPreSettleSnapshot snapshot = workInjuryOutpatientSettleOrchestrator
                .findActivePreSettleByPaymentDraft(command.getPaymentDraftId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                        "未找到工伤 2206 预结算快照，请先完成 invoke 至 2206"));
        ChargeItemQueryCommand queryCommand = new ChargeItemQueryCommand();
        queryCommand.setIds(this.parseIds(payment.getChargeItemIds()));
        List<ChargeItem> chargeItems = chargeItemQueryService.loadChargeItemsByIds(queryCommand);
        List<PaymentDetailItemCommand> draftLines = this.buildWorkInjuryPreSettlePaymentDetailDraftLines(
                command.getEncounterId(),
                chargeItems,
                snapshot.getPsnCashPay());
        BigDecimal displayAmount = snapshot.getPsnCashPay() != null
                ? snapshot.getPsnCashPay()
                : payment.getDisplayAmount();
        paymentReconciliationDomainService.refreshPaymentDraftPreSettleAmounts(
                command.getPaymentDraftId(),
                displayAmount,
                draftLines);
        return new RefreshWorkInjuryPreSettleAmountsResult()
                .setDisplayAmount(displayAmount)
                .setYbChrgBchno(snapshot.getChrgBchno())
                .setYbMedfeeSumamt(snapshot.getMedfeeSumamt())
                .setYbFundPaySumamt(snapshot.getFundPaySumamt())
                .setYbAcctPay(snapshot.getAcctPay())
                .setYbPsnCashPay(snapshot.getPsnCashPay());
    }

    private List<PaymentDetailItemCommand> buildWorkInjuryPreSettlePaymentDetailDraftLines(
            Long encounterId,
            List<ChargeItem> chargeLines,
            BigDecimal psnCashPay) {
        Long accountId = this.resolvePrimaryAccountId(encounterId, chargeLines);
        if (accountId == null) {
            return this.buildPreSettlePaymentDetailDraftLines(encounterId, chargeLines);
        }
        List<Account> encounterAccounts = accountQueryService.getAccountListByEncounter(encounterId);
        Map<Long, Account> accountById = new HashMap<>();
        for (Account accountRow : encounterAccounts) {
            accountById.put(accountRow.getId(), accountRow);
        }
        PaymentDetailItemCommand draftLine = new PaymentDetailItemCommand();
        draftLine.setAccountId(accountId);
        Account matchedAccount = accountById.get(accountId);
        if (matchedAccount != null) {
            draftLine.setAccountCode(matchedAccount.getBusNo());
        }
        draftLine.setPayEnum(PaymentChannel.CASHIER.getCode());
        BigDecimal amount = psnCashPay != null ? psnCashPay : BigDecimal.ZERO;
        draftLine.setAmount(amount);
        draftLine.setChargeAmount(amount);
        draftLine.setResultEnum(PaymentStatus.DRAFT.getCode());
        return List.of(draftLine);
    }

    private List<PaymentDetailItemCommand> buildPreSettlePaymentDetailDraftLines(
            Long encounterId,
            List<ChargeItem> chargeLines) {
        Long defaultSelfPayAccountId = accountQueryService.getSelfPayAccount(encounterId);
        Map<Long, BigDecimal> amountByAccountId = new LinkedHashMap<>();
        for (ChargeItem chargeLine : chargeLines) {
            Long lineAccountId = chargeLine.getAccountId();
            if (lineAccountId == null) {
                lineAccountId = defaultSelfPayAccountId;
            }
            if (lineAccountId == null) {
                throw new BusinessException(
                        ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                        "收费项未绑定结算账户且就诊未开立默认自费账户，无法生成支付草稿");
            }
            BigDecimal linePrice = chargeLine.getTotalPrice();
            if (linePrice == null) {
                linePrice = BigDecimal.ZERO;
            }
            amountByAccountId.merge(lineAccountId, linePrice, BigDecimal::add);
        }
        List<Account> encounterAccounts = accountQueryService.getAccountListByEncounter(encounterId);
        Map<Long, Account> accountById = new HashMap<>();
        for (Account accountRow : encounterAccounts) {
            accountById.put(accountRow.getId(), accountRow);
        }
        List<PaymentDetailItemCommand> draftLines = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : amountByAccountId.entrySet()) {
            PaymentDetailItemCommand draftLine = new PaymentDetailItemCommand();
            draftLine.setAccountId(entry.getKey());
            Account matchedAccount = accountById.get(entry.getKey());
            if (matchedAccount != null) {
                draftLine.setAccountCode(matchedAccount.getBusNo());
            }
            draftLine.setPayEnum(PaymentChannel.CASHIER.getCode());
            draftLine.setAmount(entry.getValue());
            draftLine.setChargeAmount(entry.getValue());
            draftLine.setResultEnum(PaymentStatus.DRAFT.getCode());
            draftLines.add(draftLine);
        }
        return draftLines;
    }

    private Long resolvePrimaryAccountId(Long encounterId, List<ChargeItem> chargeItems) {
        Long defaultSelfPayAccountId = accountQueryService.getSelfPayAccount(encounterId);
        for (ChargeItem item : chargeItems) {
            if (item.getAccountId() != null) {
                return item.getAccountId();
            }
        }
        return defaultSelfPayAccountId;
    }

    private List<Long> parseIds(String ids) {
        List<Long> result = new ArrayList<>();
        if (ids == null || ids.isBlank()) {
            return result;
        }
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        for (String value : ids.split(",")) {
            if (!value.isBlank()) {
                uniqueIds.add(Long.parseLong(value.trim()));
            }
        }
        result.addAll(uniqueIds);
        return result;
    }
}
