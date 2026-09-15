/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.paymentreconciliation.repository;

import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentRecDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 支付明细仓储。
 */
public interface PaymentRecDetailRepository extends JpaRepository<PaymentRecDetail, Long> {
    /**
     * 按条件查询。
     * @param reconciliationId 条件
     * @return 查询结果
     */
    List<PaymentRecDetail> findByReconciliationId(Long reconciliationId);

    /**
     * 按支付头主键批量查询明细（预交金流水支付方式展示，避免 N+1）。
     *
     * @param reconciliationIds 支付头主键集合
     * @return 明细列表
     */
    List<PaymentRecDetail> findAllByReconciliationIdIn(Collection<Long> reconciliationIds);

    /**
     * 按平台结算号查未软删的医保统筹/个账明细（2601 冲正后回滚日结虚增用）。
     *
     * @param payTransNo 平台 {@code setl_id}（落在 {@code pay_trans_no}）
     * @param payEnums   渠道码集合，通常为 {@code INSURANCE_FUND}/{@code INSURANCE_ACCT}
     * @return 未软删明细
     */
    List<PaymentRecDetail> findAllByPayTransNoAndPayEnumInAndIsDeletedFalse(
        String payTransNo,
        Collection<String> payEnums);

    /**
     * 按条件删除（受软删底座约束）。
     * @param reconciliationId 条件
     */
    void deleteByReconciliationId(Long reconciliationId);
}
