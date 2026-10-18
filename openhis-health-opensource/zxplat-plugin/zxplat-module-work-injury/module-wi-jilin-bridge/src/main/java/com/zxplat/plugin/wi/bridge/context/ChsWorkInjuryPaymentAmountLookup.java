/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.bridge.context;

import com.zxplat.domain.financial.paymentreconciliation.entity.PaymentRecDetail;
import com.zxplat.domain.financial.paymentreconciliation.entity.PaymentReconciliation;
import com.zxplat.domain.financial.paymentreconciliation.repository.PaymentRecDetailRepository;
import com.zxplat.domain.financial.paymentreconciliation.repository.PaymentReconciliationRepository;
import com.zxplat.domain.share.utils.IdInQueryChunkSupport;
import com.zxplat.plugin.wi.domain.loader.WorkInjuryPaymentAmountLookup;
import com.zxplat.plugin.wi.domain.recon.result.WorkInjuryPaymentAmountSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从 fin_payment_reconciliation + fin_payment_rec_detail 汇总收银金额。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.modules.work-injury.enabled", havingValue = "true", matchIfMissing = true)
public class ChsWorkInjuryPaymentAmountLookup implements WorkInjuryPaymentAmountLookup {

    private static final String PAY_INSURANCE_FUND = "INSURANCE_FUND";
    private static final String PAY_CASH = "CASH";

    private final PaymentReconciliationRepository paymentReconciliationRepository;
    private final PaymentRecDetailRepository paymentRecDetailRepository;

    @Override
    public Map<Long, WorkInjuryPaymentAmountSnapshot> findByPaymentIds(Collection<Long> paymentIds) {
        if (paymentIds == null || paymentIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = new HashSet<>();
        for (Long id : paymentIds) {
            if (id != null) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        // 先筛未删除支付头，再分批拉明细（避免大 IN）
        List<PaymentReconciliation> payments =
                IdInQueryChunkSupport.loadByIdInChunks(ids, paymentReconciliationRepository::findByIdIn);
        Map<Long, WorkInjuryPaymentAmountSnapshot> result = new HashMap<>();
        for (PaymentReconciliation payment : payments) {
            if (payment == null || payment.getId() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(payment.getIsDeleted())) {
                continue;
            }
            result.put(
                    payment.getId(),
                    new WorkInjuryPaymentAmountSnapshot()
                            .setPaymentId(payment.getId())
                            .setPaymentStatus(
                                    payment.getStatusEnum() == null ? null : payment.getStatusEnum().name())
                            .setPaymentMedfee(BigDecimal.ZERO)
                            .setPaymentHifp(BigDecimal.ZERO)
                            .setPaymentCash(BigDecimal.ZERO));
        }
        if (result.isEmpty()) {
            return Map.of();
        }
        List<PaymentRecDetail> details = IdInQueryChunkSupport.loadByIdInChunks(
                result.keySet(), paymentRecDetailRepository::findAllByReconciliationIdIn);
        for (PaymentRecDetail detail : details) {
            if (detail == null || detail.getReconciliationId() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(detail.getIsDeleted())) {
                continue;
            }
            WorkInjuryPaymentAmountSnapshot snap = result.get(detail.getReconciliationId());
            if (snap == null) {
                continue;
            }
            BigDecimal charge = firstNonNull(detail.getChargeAmount(), detail.getAmount());
            snap.setPaymentMedfee(snap.getPaymentMedfee().add(charge));
            String payEnum = detail.getPayEnum() == null ? "" : detail.getPayEnum().trim();
            if (PAY_INSURANCE_FUND.equalsIgnoreCase(payEnum)) {
                snap.setPaymentHifp(snap.getPaymentHifp().add(charge));
            } else if (PAY_CASH.equalsIgnoreCase(payEnum)) {
                snap.setPaymentCash(snap.getPaymentCash().add(charge));
            }
        }
        for (WorkInjuryPaymentAmountSnapshot snap : result.values()) {
            snap.setPaymentMedfee(scale2(snap.getPaymentMedfee()));
            snap.setPaymentHifp(scale2(snap.getPaymentHifp()));
            snap.setPaymentCash(scale2(snap.getPaymentCash()));
        }
        return result;
    }

    private static BigDecimal firstNonNull(BigDecimal primary, BigDecimal fallback) {
        if (primary != null) {
            return primary;
        }
        return fallback == null ? BigDecimal.ZERO : fallback;
    }

    private static BigDecimal scale2(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
