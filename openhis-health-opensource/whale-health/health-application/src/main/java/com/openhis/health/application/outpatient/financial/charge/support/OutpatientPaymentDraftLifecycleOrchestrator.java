/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.support;

import com.openhis.health.application.shared.payment.PaymentDraftHalfSuccessAbortGate;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.command.AbortPreSettlementBeforePaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 门诊预结算支付草稿生命周期：作废陈旧 DRAFT（GAP-STALE-001 / BR-OPC-012 / BR-OPC-025）。
 *
 * <p>预结算清草稿遇银联半成功 → 拒绝作废；正式结算后清其它草稿遇半成功 → 跳过保留，避免回滚已成功 settle。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutpatientPaymentDraftLifecycleOrchestrator {

    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final PaymentReconciliationDomainService paymentReconciliationDomainService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final PaymentDraftHalfSuccessAbortGate paymentDraftHalfSuccessAbortGate;

    /**
     * 作废同就诊下全部 DRAFT 支付流水（预结算前）。
     *
     * <p>半成功（BPC SUCCESS）草稿拒绝作废（BR-OPC-025），迫使补结算或先退货。</p>
     *
     * @param encounterId          就诊主键
     * @param retainDraftPaymentId 保留不作废的草稿主键；{@code null} 表示同就诊草稿全部作废
     */
    public void cancelAllDraftsForEncounter(Long encounterId, Long retainDraftPaymentId) {
        this.cancelDraftsForEncounter(encounterId, retainDraftPaymentId, false, HalfSuccessAbortPolicy.REJECT);
    }

    /**
     * 正式结算成功后：清理同就诊其它 DRAFT，半成功草稿跳过并打 WARN（不阻断本笔 settle）。
     *
     * @param encounterId          就诊主键
     * @param retainDraftPaymentId 本笔已成功支付主键（通常已非 DRAFT，传入亦不作废）
     */
    public void cancelStaleDraftsAfterSuccessfulSettle(Long encounterId, Long retainDraftPaymentId) {
        this.cancelDraftsForEncounter(encounterId, retainDraftPaymentId, false, HalfSuccessAbortPolicy.SKIP);
    }

    /**
     * 签退等上游变更后：作废挂载项中仍存在非 {@link ChargeItemStatus#PLANNED} 的 DRAFT 支付流水。
     *
     * @param encounterId 就诊主键
     */
    public void cancelDraftsWithNonPlannedBoundCharges(Long encounterId) {
        this.cancelDraftsForEncounter(encounterId, null, true, HalfSuccessAbortPolicy.REJECT);
    }

    /**
     * 作废就诊下 DRAFT 预结算流水（可选保留指定 draft、仅处理绑定非 PLANNED 费用的 draft）。
     *
     * @param encounterId           就诊主键
     * @param retainDraftPaymentId  保留的 draft 支付 id；null 不保留
     * @param onlyNonPlannedBound   true 时跳过绑定费用均为 PLANNED 的 draft
     * @param halfSuccessPolicy     半成功草稿：拒绝作废或跳过保留
     */
    private void cancelDraftsForEncounter(
        Long encounterId,
        Long retainDraftPaymentId,
        boolean onlyNonPlannedBound,
        HalfSuccessAbortPolicy halfSuccessPolicy) {
        if (encounterId == null) {
            return;
        }
        List<PaymentReconciliationQueryModel> encounterPayments =
            paymentReconciliationQueryService.findPaymentsByEncounter(encounterId);
        if (encounterPayments == null || encounterPayments.isEmpty()) {
            return;
        }
        for (PaymentReconciliationQueryModel payment : encounterPayments) {
            if (payment == null || payment.getId() == null) {
                continue;
            }
            // 仅清理草稿态；SUCCESS/CANCELLED 等终态不动
            if (!PaymentStatus.DRAFT.equals(payment.getStatusEnum())) {
                continue;
            }
            // 保留本笔预结算/结算目标草稿
            if (retainDraftPaymentId != null && retainDraftPaymentId.equals(payment.getId())) {
                continue;
            }
            // 签退路径：绑定费用仍全 PLANNED 的草稿视为有效预结算，保留
            if (onlyNonPlannedBound && !this.hasNonPlannedBoundChargeItem(payment)) {
                continue;
            }
            // Gate：半成功（银联已扣）——预结算清草稿拒绝；结算后清草稿跳过保留
            if (paymentDraftHalfSuccessAbortGate.hasUnreimbursedSuccessfulScan(payment.getId())) {
                if (HalfSuccessAbortPolicy.REJECT.equals(halfSuccessPolicy)) {
                    paymentDraftHalfSuccessAbortGate.assertAbortableWithoutUnreimbursedScan(payment.getId());
                }
                log.warn(
                    "skip abort payment draft with unreimbursed BPC SUCCESS paymentId={} encounterId={}",
                    payment.getId(),
                    encounterId);
                continue;
            }
            AbortPreSettlementBeforePaymentCommand abortCommand = new AbortPreSettlementBeforePaymentCommand();
            abortCommand.setPaymentId(payment.getId());
            paymentReconciliationDomainService.abortPreSettlementBeforePayment(abortCommand);
        }
    }

    /**
     * 支付流水挂载的费用中是否存在非 {@link ChargeItemStatus#PLANNED} 项（缺挂载或缺行视为 true）。
     *
     * @param payment DRAFT 支付读模型
     * @return 存在非 PLANNED 绑定为 true
     */
    private boolean hasNonPlannedBoundChargeItem(PaymentReconciliationQueryModel payment) {
        List<Long> boundChargeItemIds = this.parseChargeItemIds(payment.getChargeItemIds());
        if (boundChargeItemIds.isEmpty()) {
            return true;
        }
        ChargeItemQueryCommand loadBoundItems = new ChargeItemQueryCommand();
        loadBoundItems.setIds(boundChargeItemIds);
        List<ChargeItem> boundChargeItems = chargeItemQueryService.loadChargeItemsByIds(loadBoundItems);
        Map<Long, ChargeItem> boundById = new HashMap<>();
        for (ChargeItem boundChargeItem : boundChargeItems) {
            if (boundChargeItem != null && boundChargeItem.getId() != null) {
                boundById.put(boundChargeItem.getId(), boundChargeItem);
            }
        }
        for (Long chargeItemId : boundChargeItemIds) {
            ChargeItem bound = boundById.get(chargeItemId);
            // 挂载 id 在库中缺失 → 视为陈旧草稿，允许清理
            if (bound == null) {
                return true;
            }
            // 任一行已非待收费 → 签退后应作废该预结算草稿
            if (!ChargeItemStatus.PLANNED.getCode().equals(bound.getStatusEnum())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析支付流水 chargeItemIds 逗号串为有序去重 id 列表。
     *
     * @param ids 持久化逗号分隔串
     * @return 解析成功的 id 列表；空串返回空列表
     */
    private List<Long> parseChargeItemIds(String ids) {
        List<Long> result = new ArrayList<>();
        if (ids == null || ids.isBlank()) {
            return result;
        }
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        String[] split = ids.split(",");
        for (String value : split) {
            if (!value.isBlank()) {
                try {
                    Long parsedId = Long.parseLong(value.trim());
                    if (uniqueIds.add(parsedId)) {
                        result.add(parsedId);
                    }
                } catch (NumberFormatException ex) {
                    throw new BusinessException(
                        ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                        "支付草稿收费项数据格式非法");
                }
            }
        }
        return result;
    }

    /**
     * 半成功草稿作废策略：预结算拒绝；结算后清残留则跳过。
     */
    private enum HalfSuccessAbortPolicy {
        /** 抛业务异常，禁止作废。 */
        REJECT,
        /** 保留草稿并打 WARN，不阻断调用方事务。 */
        SKIP
    }
}
