/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.paymentreconciliation.repository;

import com.zxplat.domain.financial.paymentreconciliation.entity.PaymentRecStatic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 支付统计快照仓储。
 */
public interface PaymentRecStaticRepository extends JpaRepository<PaymentRecStatic, Long> {
    /**
     * 按条件查询。
     * @param reconciliationId 条件
     * @return 查询结果
     */
    List<PaymentRecStatic> findByReconciliationId(Long reconciliationId);

    /**
     * 按支付流水主键批量读取统计快照（日结财务类别汇总）。
     *
     * @param reconciliationIds 支付流水主键集合
     * @return 快照行列表
     */
    List<PaymentRecStatic> findAllByReconciliationIdInAndIsDeletedFalse(Collection<Long> reconciliationIds);

    /**
     * 按条件删除（受软删底座约束）。
     * @param reconciliationId 条件
     */
    void deleteByReconciliationId(Long reconciliationId);
}
