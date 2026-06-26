/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
