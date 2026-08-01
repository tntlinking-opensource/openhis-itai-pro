/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.settlement.service;

import com.openhis.chs.domain.settlement.entity.YbSettlementReversal;
import com.openhis.chs.domain.settlement.repository.YbSettlementReversalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 结算撤销凭证只读查询（本聚合 {@link YbSettlementReversalRepository}）。
 * <p>供对账清算按撤销时间窗装载反向笔，与正向结算快照配对做金额抵净与笔数统计。</p>
 */
@Service
@RequiredArgsConstructor
public class YbSettlementReversalQueryService {

    private final YbSettlementReversalRepository reversalRepository;

    /**
     * 查询指定撤销时间窗内的撤销凭证（左闭右开）。
     *
     * @param tenantId     租户 ID
     * @param beginAt      起始时间（含）
     * @param endExclusive 结束时间（不含）
     * @return 撤销凭证列表；参数缺失时空列表
     */
    public List<YbSettlementReversal> listReversedBetween(
            Long tenantId, LocalDateTime beginAt, LocalDateTime endExclusive) {
        if (tenantId == null || beginAt == null || endExclusive == null) {
            return Collections.emptyList();
        }
        return reversalRepository
            .findAllByTenantIdAndIsDeletedFalseAndReversedAtGreaterThanEqualAndReversedAtLessThan(
                tenantId, beginAt, endExclusive);
    }
}
