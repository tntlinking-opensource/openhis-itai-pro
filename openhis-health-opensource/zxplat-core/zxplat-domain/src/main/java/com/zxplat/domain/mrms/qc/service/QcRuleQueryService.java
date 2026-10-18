/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.mrms.qc.service;

import com.zxplat.domain.mrms.qc.entity.QcRule;
import com.zxplat.domain.mrms.qc.entity.RequiredDocType;
import com.zxplat.domain.mrms.qc.repository.QcRuleRepository;
import com.zxplat.domain.mrms.qc.repository.RequiredDocTypeRepository;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 质控规则与必写文书配置读侧查询（仅本聚合 Repository）。
 *
 * <p>合并 {@link GeneralSharedConstants#PLATFORM_TENANT_ID} 种子与租户自定义行：同 code 时租户行优先。</p>
 */
@Service
@RequiredArgsConstructor
public class QcRuleQueryService {

    private final QcRuleRepository qcRuleRepository;
    private final RequiredDocTypeRepository requiredDocTypeRepository;

    /**
     * 加载病案租户下已启用的质控规则（种子 + 租户覆盖）。
     *
     * @param tenantId 病案所属租户
     * @return 按 {@code rule_code} 去重后的规则列表
     */
    public List<QcRule> listEnabledRules(Long tenantId) {
        List<QcRule> loaded = qcRuleRepository.findByTenantIdInAndEnabledTrueAndIsDeletedFalse(
                tenantIdsForMerge(tenantId));
        return mergeByCode(
                loaded,
                QcRule::getRuleCode,
                tenantId,
                QcRule::getTenantId);
    }

    /**
     * 加载病案租户下必写文书配置（种子 + 租户覆盖，可按就诊类别过滤）。
     *
     * @param tenantId 病案所属租户
     * @param encounterClassCode 就诊类别 code；null 时仅保留未限定类别的配置
     * @return 按 {@code document_type_code} 去重后的必写文书行
     */
    public List<RequiredDocType> listRequiredDocTypes(Long tenantId, String encounterClassCode) {
        List<RequiredDocType> loaded = requiredDocTypeRepository.findByTenantIdInAndRequiredTrueAndIsDeletedFalse(
                tenantIdsForMerge(tenantId));
        List<RequiredDocType> matched = new ArrayList<>();
        for (RequiredDocType row : loaded) {
            // 就诊类别为空时匹配全部；否则须与配置一致
            if (matchesEncounterClass(row.getEncounterClassCode(), encounterClassCode)) {
                matched.add(row);
            }
        }
        return mergeByCode(
                matched,
                RequiredDocType::getDocumentTypeCode,
                tenantId,
                RequiredDocType::getTenantId);
    }

    /** 种子租户 + 业务租户 id 列表（内存 merge 用）。 */
    private Collection<Long> tenantIdsForMerge(Long tenantId) {
        List<Long> tenantIds = new ArrayList<>();
        tenantIds.add(GeneralSharedConstants.PLATFORM_TENANT_ID);
        if (tenantId != null && !tenantId.equals(GeneralSharedConstants.PLATFORM_TENANT_ID)) {
            tenantIds.add(tenantId);
        }
        return tenantIds;
    }

    /** 同 code 种子行先占位，租户行后覆盖。 */
    private <T> List<T> mergeByCode(
            List<T> loaded,
            Function<T, String> codeExtractor,
            Long tenantId,
            Function<T, Long> rowTenantIdExtractor) {
        Map<String, T> byCode = new LinkedHashMap<>();
        for (T row : loaded) {
            String code = codeExtractor.apply(row);
            // 无 code 的行不参与 merge
            if (code == null) {
                continue;
            }
            byCode.putIfAbsent(code, row);
        }
        if (tenantId != null && !tenantId.equals(GeneralSharedConstants.PLATFORM_TENANT_ID)) {
            // 租户自定义行覆盖同 code 种子行
            for (T row : loaded) {
                // 跳过种子租户行
                if (!tenantId.equals(rowTenantIdExtractor.apply(row))) {
                    continue;
                }
                String code = codeExtractor.apply(row);
                if (code != null) {
                    byCode.put(code, row);
                }
            }
        }
        return new ArrayList<>(byCode.values());
    }

    /** 配置未限定 encounterClass 时匹配全部；否则须与病案就诊类别一致。 */
    private boolean matchesEncounterClass(String configuredClass, String encounterClassCode) {
        String configured = trimToNull(configuredClass);
        // 配置未限定类别 → 全部就诊匹配
        if (configured == null) {
            return true;
        }
        return configured.equals(encounterClassCode);
    }
}
