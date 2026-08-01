/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.bridge.issue.support;

import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.terminology.entity.Concept;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 博思票面单位：将 health {@code quantity_unit} 术语 code 转为中文 display。
 *
 * <p>收费项存的是术语码（如 {@code 6}/{@code branch}/{@code bottle}），票面须出「次/支/瓶」。
 * 新平台计量单位已合并至 {@code unit_code}；JLCC 等存量仍可能落在 dose/split/sales 等分码表。</p>
 */
@Component
@RequiredArgsConstructor
public class BossQuantityUnitDisplayResolver {

    /**
     * 解析顺序：统一 {@code unit_code} 优先，再回落剂量与旧五类单位码表。
     */
    private static final List<String> UNIT_CODE_SYSTEMS = List.of(
            TerminologyCodeSystemNameConstants.UNIT_CODE,
            TerminologyCodeSystemNameConstants.DOSE_UNIT,
            "split_unit",
            "sales_unit",
            "purchase_unit",
            "stock_unit");

    private final CodeSystemQueryService codeSystemQueryService;

    /**
     * 预热「码 → 中文展示」映射，供同批次多行复用。
     *
     * @return 不可变语义上的可变 HashMap；调用方只读
     */
    public Map<String, String> warmLabelMap() {
        Map<String, String> labelByCode = new HashMap<>();
        for (String systemName : UNIT_CODE_SYSTEMS) {
            List<Concept> concepts = codeSystemQueryService.findConceptsByCodeSystemName(systemName);
            for (Concept concept : concepts) {
                if (concept.getCode() == null || !StringUtils.hasText(concept.getDisplay())) {
                    continue;
                }
                labelByCode.putIfAbsent(String.valueOf(concept.getCode()).trim(), concept.getDisplay().trim());
            }
        }
        return labelByCode;
    }

    /**
     * 将单位 code 转为票面中文；已是中文或未命中术语时原样返回。
     *
     * @param quantityUnit 收费项/明细上的单位码或已是展示文案
     * @param labelByCode  {@link #warmLabelMap()} 预热结果；null 时当场查单码
     * @return 票面单位；入参空白则原样返回
     */
    public String toDisplay(String quantityUnit, Map<String, String> labelByCode) {
        if (!StringUtils.hasText(quantityUnit)) {
            return quantityUnit;
        }
        String code = quantityUnit.trim();
        if (labelByCode != null && !labelByCode.isEmpty()) {
            String fromMap = labelByCode.get(code);
            return StringUtils.hasText(fromMap) ? fromMap : code;
        }
        for (String systemName : UNIT_CODE_SYSTEMS) {
            Concept concept = codeSystemQueryService.findConceptByCodeSystemNameAndCode(systemName, code);
            if (concept != null && StringUtils.hasText(concept.getDisplay())) {
                return concept.getDisplay().trim();
            }
        }
        return code;
    }

    /**
     * 单条解析（无预热）。
     *
     * @param quantityUnit 单位码
     * @return 票面中文单位
     */
    public String toDisplay(String quantityUnit) {
        return toDisplay(quantityUnit, null);
    }
}
