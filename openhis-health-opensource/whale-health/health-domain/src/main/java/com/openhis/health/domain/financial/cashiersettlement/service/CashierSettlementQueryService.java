/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.cashiersettlement.service;

import com.openhis.health.domain.financial.cashiersettlement.entity.CashierSettlement;
import com.openhis.health.domain.financial.cashiersettlement.repository.CashierSettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 收费员交账头读侧领域服务（DM-FIN-003 §5.2，仅本聚合）。
 *
 * <p>未交账 payment 候选预览在 {@code PaymentReconciliationQueryService}（B.6.4 禁止跨聚合 Repository）。</p>
 */
@Service
@RequiredArgsConstructor
public class CashierSettlementQueryService {

    private final CashierSettlementRepository cashierSettlementRepository;

    /**
     * 按交账头主键读取交账单号（提交成功后装配响应用）。
     *
     * @param settlementId 交账头主键
     * @return 交账单号；不存在时返回 {@code null}
     */
    public String findBusNoBySettlementId(Long settlementId) {
        // 写侧未返回有效 id 时不查库
        if (settlementId == null) {
            return null;
        }
        CashierSettlement settlement = this.cashierSettlementRepository.findById(settlementId).orElse(null);
        // 交账头不存在时不抛异常，由 Asm 装配 null busNo
        if (settlement == null) {
            return null;
        }
        return settlement.getBusNo();
    }
}
