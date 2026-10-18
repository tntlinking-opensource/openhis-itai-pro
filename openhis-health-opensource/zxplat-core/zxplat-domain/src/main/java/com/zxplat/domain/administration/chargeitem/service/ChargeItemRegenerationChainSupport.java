/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.chargeitem.service;

import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.repository.ChargeItemRepository;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 收费明细半退/住院反结再生链解析（BR-REF-020 / BR-IP-RVS-011）。
 *
 * <p>多轮「结算 → 反结 → 再结算」会形成 {@code SETTLED → … → PENDING_SETTLEMENT} 多层链；
 * Gate 与读侧须沿 {@code refund_id} 向上追溯整条链，凡链末端存在有效再生叶，则链上全部祖先 SETTLED 均视为终态历史。</p>
 */
@Component
@RequiredArgsConstructor
public class ChargeItemRegenerationChainSupport {

    private final ChargeItemRepository repository;

    /**
     * 自就诊全部费用行解析：凡被有效再生叶替代的 SETTLED 祖先主键集合。
     *
     * @param encounterId 就诊主键
     * @return 应忽略的 SETTLED 祖先主键；无链时空集合
     */
    public Set<Long> collectSupersededSettledAncestorIds(Long encounterId) {
        if (encounterId == null) {
            return Collections.emptySet();
        }
        List<ChargeItem> encounterRows = repository.findAllByEncounterId(encounterId);
        return this.collectSupersededSettledAncestorIdsFromRows(encounterRows);
    }

    /**
     * 自已装载就诊费用行解析：凡被有效再生叶替代的 SETTLED 祖先主键集合。
     *
     * @param encounterRows 单就诊费用行（可含跨状态）
     * @return 应忽略的 SETTLED 祖先主键；无链时空集合
     */
    public Set<Long> collectSupersededSettledAncestorIdsFromRows(List<ChargeItem> encounterRows) {
        if (encounterRows == null || encounterRows.isEmpty()) {
            return Collections.emptySet();
        }
        Map<Long, ChargeItem> byId = new HashMap<>();
        for (ChargeItem row : encounterRows) {
            if (row != null && row.getId() != null) {
                byId.put(row.getId(), row);
            }
        }
        LinkedHashSet<Long> supersededAncestorIds = new LinkedHashSet<>();
        for (ChargeItem row : encounterRows) {
            if (row == null || !this.isActiveSupersedingRegenerationStatus(row.getStatusEnum())) {
                continue;
            }
            Long ancestorId = row.getRefundId();
            while (ancestorId != null) {
                supersededAncestorIds.add(ancestorId);
                ChargeItem ancestor = byId.get(ancestorId);
                if (ancestor == null) {
                    break;
                }
                ancestorId = ancestor.getRefundId();
            }
        }
        return supersededAncestorIds;
    }

    /**
     * 解析 SETTLED 祖先 → 链末端有效再生叶主键（多跳；门诊半退 PLANNED、住院反结 PENDING_SETTLEMENT）。
     *
     * @param encounterId 就诊主键
     * @return 祖先主键 → 有效再生叶主键
     */
    public Map<Long, Long> mapSettledAncestorToActiveRegenerationLeafId(Long encounterId) {
        if (encounterId == null) {
            return Collections.emptyMap();
        }
        List<ChargeItem> encounterRows = repository.findAllByEncounterId(encounterId);
        Map<Long, ChargeItem> byId = new HashMap<>();
        for (ChargeItem row : encounterRows) {
            if (row != null && row.getId() != null) {
                byId.put(row.getId(), row);
            }
        }
        Map<Long, Long> ancestorToLeaf = new LinkedHashMap<>();
        for (ChargeItem row : encounterRows) {
            if (row == null || row.getId() == null || !this.isActiveSupersedingRegenerationStatus(row.getStatusEnum())) {
                continue;
            }
            Long leafId = row.getId();
            Long ancestorId = row.getRefundId();
            while (ancestorId != null) {
                ancestorToLeaf.put(ancestorId, leafId);
                ChargeItem ancestor = byId.get(ancestorId);
                if (ancestor == null) {
                    break;
                }
                ancestorId = ancestor.getRefundId();
            }
        }
        return ancestorToLeaf;
    }

    /**
     * 收费状态是否为有效再生叶（门诊半退 {@code PLANNED}、住院反结 {@code PENDING_SETTLEMENT}）。
     *
     * @param statusEnum 收费状态 code
     * @return 有效再生叶时 true
     */
    public boolean isActiveSupersedingRegenerationStatus(String statusEnum) {
        if (ChargeItemStatus.PLANNED.getCode().equals(statusEnum)) {
            return true;
        }
        if (ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(statusEnum)) {
            return true;
        }
        return false;
    }
}
