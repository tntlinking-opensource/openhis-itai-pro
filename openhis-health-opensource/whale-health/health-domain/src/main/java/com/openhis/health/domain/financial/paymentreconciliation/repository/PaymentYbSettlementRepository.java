/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.paymentreconciliation.repository;

import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentYbSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** 支付流水医保结算凭证仓储。 */
public interface PaymentYbSettlementRepository extends JpaRepository<PaymentYbSettlement, Long> {

    List<PaymentYbSettlement> findAllByReconciliationIdAndIsDeletedFalseOrderByCreatedAtAsc(Long reconciliationId);

    List<PaymentYbSettlement> findAllByReconciliationIdInAndIsDeletedFalseOrderByReconciliationIdAscCreatedAtAsc(
        Collection<Long> reconciliationIds);

    /**
     * 按 CHS 结算回执主键查支付侧桥表行（2208/2305 撤销回写用）。
     *
     * @param settlementRecordId {@code yb_settlement_record.id}
     * @return 未软删行
     */
    List<PaymentYbSettlement> findAllBySettlementRecordIdAndIsDeletedFalse(Long settlementRecordId);

    /**
     * 按平台 setl_id 查支付侧桥表行（无 settlement_record_id 时的回落）。
     *
     * @param setlId 平台结算号
     * @return 未软删行
     */
    List<PaymentYbSettlement> findAllBySetlIdAndIsDeletedFalse(String setlId);

    void deleteByReconciliationId(Long reconciliationId);
}
