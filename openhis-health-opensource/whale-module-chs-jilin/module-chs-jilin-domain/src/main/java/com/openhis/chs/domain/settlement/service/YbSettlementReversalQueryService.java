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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

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

    /**
     * 按平台撤销结算号批量装载未删除撤销凭证（3202 退费结果行本院补齐）。
     *
     * @param revokeSetlIds 平台 revoke_setl_id / 退费 setl_id 集合
     * @param tenantId      租户 ID
     * @return revokeSetlId → 凭证；参数缺失或未命中时空 map
     */
    public Map<String, YbSettlementReversal> mapByRevokeSetlIds(
            Collection<String> revokeSetlIds, Long tenantId) {
        if (revokeSetlIds == null || revokeSetlIds.isEmpty() || tenantId == null) {
            return Collections.emptyMap();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String setlId : revokeSetlIds) {
            if (setlId != null && !setlId.isBlank()) {
                unique.add(setlId.trim());
            }
        }
        if (unique.isEmpty()) {
            return Collections.emptyMap();
        }
        List<YbSettlementReversal> rows =
            reversalRepository.findAllByRevokeSetlIdInAndTenantIdAndIsDeletedFalse(unique, tenantId);
        Map<String, YbSettlementReversal> out = new HashMap<>(rows.size() * 2);
        for (YbSettlementReversal row : rows) {
            if (row == null || row.getRevokeSetlId() == null || row.getRevokeSetlId().isBlank()) {
                continue;
            }
            out.putIfAbsent(row.getRevokeSetlId().trim(), row);
        }
        return out;
    }
}
