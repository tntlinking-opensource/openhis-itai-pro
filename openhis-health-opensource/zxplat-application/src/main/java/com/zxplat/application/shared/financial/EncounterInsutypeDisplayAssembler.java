/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.financial;

import com.zxplat.domain.administration.account.entity.Account;
import com.zxplat.domain.administration.account.service.AccountQueryService;
import com.zxplat.domain.infrastructure.integration.insurance.benefit.InsuranceRegisterBenefitQueryExtension;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 就诊险种类型（insutype）批量装配：医生站患者卡片「职工/居民」短标签。
 *
 * <p>优先就诊医保账户 {@code adm_account.insutype}；空时回落医保扩展
 * （读卡缓存含已过期 + 门诊预结算），与发药二级险种同源数据。</p>
 */
@Component
@RequiredArgsConstructor
public class EncounterInsutypeDisplayAssembler {

    /** 就诊账户读侧：医保户险种。 */
    private final AccountQueryService accountQueryService;
    /** 医保待遇扩展：人员缓存/预结算回落。 */
    private final InsuranceRegisterBenefitQueryExtension insuranceRegisterBenefitQueryExtension;

    /**
     * 批量解析就诊险种码（310/390 等）。
     *
     * @param encounterIds 就诊主键集合
     * @return 就诊主键 → insutype；无码不含键
     */
    public Map<Long, String> mapInsutypeByEncounterIds(Collection<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : encounterIds) {
            if (id != null) {
                unique.add(id);
            }
        }
        if (unique.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> out = new HashMap<>(unique.size() * 2);
        Map<Long, Account> miAccountByEncounterId =
            accountQueryService.mapMedicalInsuranceAccountsByEncounterIds(unique);
        for (Map.Entry<Long, Account> entry : miAccountByEncounterId.entrySet()) {
            Account account = entry.getValue();
            if (entry.getKey() == null || account == null || StringUtils.isEmpty(account.getInsutype())) {
                continue;
            }
            out.put(entry.getKey(), account.getInsutype().trim());
        }
        Set<Long> missing = new HashSet<>();
        for (Long encounterId : unique) {
            if (!out.containsKey(encounterId)) {
                missing.add(encounterId);
            }
        }
        if (missing.isEmpty()) {
            return out;
        }
        Map<Long, String> fromExtension =
            insuranceRegisterBenefitQueryExtension.mapInsutypeByEncounterIds(missing);
        if (fromExtension == null || fromExtension.isEmpty()) {
            return out;
        }
        for (Map.Entry<Long, String> entry : fromExtension.entrySet()) {
            if (entry.getKey() == null || StringUtils.isEmpty(entry.getValue()) || out.containsKey(entry.getKey())) {
                continue;
            }
            out.put(entry.getKey(), entry.getValue().trim());
        }
        return out;
    }
}
