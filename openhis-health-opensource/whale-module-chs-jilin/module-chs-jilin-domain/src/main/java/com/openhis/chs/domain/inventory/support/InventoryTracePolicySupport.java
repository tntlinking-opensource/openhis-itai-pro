/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.support;

import com.openhis.chs.domain.share.enums.TrdnFlag;
import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.chs.domain.inventory.config.ChsInventoryProperties;
import java.util.List;
import java.util.Objects;

/**
 * 进销存追溯码策略（tracePolicy）判定工具（§4.8.6）。
 */
public final class InventoryTracePolicySupport {

    /**
     * 工具类禁止实例化。
     */
    private InventoryTracePolicySupport() {
    }

    /**
     * 根据医保目录编码、品种分类与发药单位判定追溯码策略。
     *
     * @param ybNo                  医保目录编码，空则无需追溯
     * @param categoryCode          品种目录分类编码
     * @param defEncounterUnitCode  默认就诊单位编码
     * @param dispenseUnitCode      实际发药单位编码
     * @param inventoryProperties   进销存配置
     * @return 追溯码策略常量（如 TRACE_REQUIRED、NO_CODE_LIBRARY 等）
     */
    public static String resolve(
            String ybNo,
            String categoryCode,
            String defEncounterUnitCode,
            String dispenseUnitCode,
            ChsInventoryProperties inventoryProperties) {
        if (isEmpty(ybNo)) {
            return ChsInventoryConstants.TRACE_POLICY_NO_YB_NO;
        }
        if (matchesCatalogType(categoryCode, inventoryProperties.getPolicyExemptCatalogTypes())) {
            return ChsInventoryConstants.TRACE_POLICY_POLICY_EXEMPT;
        }
        if (matchesCatalogType(categoryCode, inventoryProperties.getNoCodeLibraryCatalogTypes())) {
            return ChsInventoryConstants.TRACE_POLICY_NO_CODE_LIBRARY;
        }
        if (TrdnFlag.SPLIT.getCode().equals(
            resolveTrdnFlag(defEncounterUnitCode, dispenseUnitCode))) {
            return ChsInventoryConstants.TRACE_POLICY_SPLIT_DISPENSE;
        }
        if (inventoryProperties.isTraceRequiredDefault()) {
            return ChsInventoryConstants.TRACE_POLICY_TRACE_REQUIRED;
        }
        return ChsInventoryConstants.TRACE_POLICY_NO_CODE_LIBRARY;
    }

    /**
     * 根据默认就诊单位与实际发药单位判定拆零标志。
     *
     * @param defEncounterUnitCode 默认就诊单位编码
     * @param dispenseUnitCode     实际发药单位编码
     * @return 拆零标志：0 整盒，1 拆零
     */
    public static String resolveTrdnFlag(String defEncounterUnitCode, String dispenseUnitCode) {
        if (isNotEmpty(defEncounterUnitCode)
            && Objects.equals(defEncounterUnitCode, dispenseUnitCode)) {
            return TrdnFlag.WHOLE.getCode();
        }
        return TrdnFlag.SPLIT.getCode();
    }

    /**
     * 判断品种分类编码是否命中配置的白名单目录类型。
     *
     * @param categoryCode    品种目录分类编码
     * @param configuredTypes 配置的目录类型白名单
     * @return 命中白名单时 {@code true}
     */
    private static boolean matchesCatalogType(String categoryCode, List<String> configuredTypes) {
        if (isEmpty(categoryCode) || configuredTypes == null || configuredTypes.isEmpty()) {
            return false;
        }
        String normalized = categoryCode.trim();
        for (String configured : configuredTypes) {
            if (configured != null && normalized.equalsIgnoreCase(configured.trim())) {
                return true;
            }
        }
        return false;
    }
}
