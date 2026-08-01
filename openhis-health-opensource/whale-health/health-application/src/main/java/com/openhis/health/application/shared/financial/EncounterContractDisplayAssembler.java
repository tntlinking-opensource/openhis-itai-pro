/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.financial;

import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 就诊费用性质（合同）展示名批量装配：医生站/护士站患者卡片共用。
 *
 * <p>合同号优先取医保账户，其次登记个人现金账户（与 {@link AccountQueryService#mapPreferredContractNoByEncounterIds}
 * 同口径），再经 {@code fin_contract.bus_no} 解析名称。</p>
 */
@Component
@RequiredArgsConstructor
public class EncounterContractDisplayAssembler {

    /** 就诊账户读侧：解析优先合同号。 */
    private final AccountQueryService accountQueryService;
    /** 费用性质主数据读侧：合同号 → 名称。 */
    private final ContractQueryService contractQueryService;

    /**
     * 批量解析就诊费用性质展示名（有医保账户优先医保合同名）。
     *
     * @param encounterIds 就诊主键集合
     * @return 就诊主键 → 合同名称；无合同时不含键
     */
    public Map<Long, String> mapContractDisplayByEncounterIds(Collection<Long> encounterIds) {
        Map<Long, String> contractNoByEncounterId =
            accountQueryService.mapPreferredContractNoByEncounterIds(encounterIds);
        if (contractNoByEncounterId.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<String> contractBusNos = new HashSet<>();
        for (String contractNo : contractNoByEncounterId.values()) {
            if (contractNo != null && !contractNo.isEmpty()) {
                contractBusNos.add(contractNo);
            }
        }
        // Pre：一次批量解析合同名，避免按就诊 N+1 查主数据
        Map<String, String> contractNameByBusNo =
            contractQueryService.mapContractNameByBusNoIn(contractBusNos);
        Map<Long, String> out = new HashMap<>(contractNoByEncounterId.size() * 2);
        for (Map.Entry<Long, String> entry : contractNoByEncounterId.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String name = contractNameByBusNo.get(entry.getValue());
            if (name == null || name.isEmpty()) {
                continue;
            }
            out.put(entry.getKey(), name);
        }
        return out;
    }
}
