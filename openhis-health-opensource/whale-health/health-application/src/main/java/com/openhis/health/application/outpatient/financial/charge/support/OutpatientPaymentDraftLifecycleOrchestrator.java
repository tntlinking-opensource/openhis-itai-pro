/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.support;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.command.AbortPreSettlementBeforePaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 门诊预结算支付草稿生命周期：作废陈旧 DRAFT（GAP-STALE-001 / BR-OPC-012）。
 */
@Component
@RequiredArgsConstructor
public class OutpatientPaymentDraftLifecycleOrchestrator {

    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final PaymentReconciliationDomainService paymentReconciliationDomainService;
    private final ChargeItemQueryService chargeItemQueryService;

    /**
     * 作废同就诊下全部 DRAFT 支付流水（预结算前 / 正式结算成功后）。
     *
     * @param encounterId          就诊主键
     * @param retainDraftPaymentId 保留不作废的草稿主键；{@code null} 表示同就诊草稿全部作废
     */
    public void cancelAllDraftsForEncounter(Long encounterId, Long retainDraftPaymentId) {
        this.cancelDraftsForEncounter(encounterId, retainDraftPaymentId, false);
    }

    /**
     * 签退等上游变更后：作废挂载项中仍存在非 {@link ChargeItemStatus#PLANNED} 的 DRAFT 支付流水。
     *
     * @param encounterId 就诊主键
     */
    public void cancelDraftsWithNonPlannedBoundCharges(Long encounterId) {
        this.cancelDraftsForEncounter(encounterId, null, true);
    }

    /**
     * 作废就诊下 DRAFT 预结算流水（可选保留指定 draft、仅处理绑定非 PLANNED 费用的 draft）。
     *
     * @param encounterId           就诊主键
     * @param retainDraftPaymentId  保留的 draft 支付 id；null 不保留
     * @param onlyNonPlannedBound   true 时跳过绑定费用均为 PLANNED 的 draft
     */
    private void cancelDraftsForEncounter(Long encounterId, Long retainDraftPaymentId, boolean onlyNonPlannedBound) {
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
            if (!PaymentStatus.DRAFT.equals(payment.getStatusEnum())) {
                continue;
            }
            if (retainDraftPaymentId != null && retainDraftPaymentId.equals(payment.getId())) {
                continue;
            }
            if (onlyNonPlannedBound && !this.hasNonPlannedBoundChargeItem(payment)) {
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
            if (bound == null) {
                return true;
            }
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
}
