/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.support;

import com.openhis.chs.domain.share.enums.TrdnFlag;
import static com.openhis.whale.core.application.util.StringUtils.isEmpty;

import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.chs.domain.inventory.config.ChsInventoryProperties;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * 进销存追溯码策略（tracePolicy）与拆零标志判定（DD §4.2）。
 *
 * <p>{@code trdn_flag} 最终真相：已扫认各码实扣——任一码非「满贴码单位整出」→ {@code 1}；
 * 全部为满码整出 → {@code 0}；未扫默认 {@code 1}。单位余数公式仅作无实扣时的辅助回落。</p>
 */
public final class InventoryTracePolicySupport {

    /**
     * 工具类禁止实例化。
     */
    private InventoryTracePolicySupport() {
    }

    /**
     * 根据医保目录编码、品种分类与拆零标志判定追溯码策略。
     *
     * @param ybNo                医保目录编码，空则无需追溯
     * @param categoryCode        品种目录分类编码
     * @param trdnFlag            已判定的拆零标志（0/1）
     * @param inventoryProperties 进销存配置
     * @return 追溯码策略常量
     */
    public static String resolve(
            String ybNo,
            String categoryCode,
            String trdnFlag,
            ChsInventoryProperties inventoryProperties) {
        // 无医保目录编码：整条明细不上报追溯
        if (isEmpty(ybNo)) {
            return ChsInventoryConstants.TRACE_POLICY_NO_YB_NO;
        }
        // 地方政策豁免目录
        if (matchesCatalogType(categoryCode, inventoryProperties.getPolicyExemptCatalogTypes())) {
            return ChsInventoryConstants.TRACE_POLICY_POLICY_EXEMPT;
        }
        // 无码库品种
        if (matchesCatalogType(categoryCode, inventoryProperties.getNoCodeLibraryCatalogTypes())) {
            return ChsInventoryConstants.TRACE_POLICY_NO_CODE_LIBRARY;
        }
        // 拆零发药：按拆零采集规则
        if (TrdnFlag.SPLIT.getCode().equals(trdnFlag)) {
            return ChsInventoryConstants.TRACE_POLICY_SPLIT_DISPENSE;
        }
        // 配置要求必须带追溯码
        if (inventoryProperties.isTraceRequiredDefault()) {
            return ChsInventoryConstants.TRACE_POLICY_TRACE_REQUIRED;
        }
        return ChsInventoryConstants.TRACE_POLICY_NO_CODE_LIBRARY;
    }

    /**
     * 未扫码默认拆零标志（DD §4.2）：默认 {@code 1}。
     *
     * @return 拆零标志 1
     */
    public static String resolveTrdnFlagUnscannedDefault() {
        return TrdnFlag.SPLIT.getCode();
    }

    /**
     * 无实扣明细时的辅助回落：单位不等 → 拆零；单位相等 → 整包装；缺单位 → 未扫默认 1。
     *
     * @param operationUnitCode 业务操作单位
     * @param traceUnitCode     追溯码主档单位；空则按未扫默认 1
     * @return 0 整包装；1 拆零
     */
    public static String resolveTrdnFlag(String operationUnitCode, String traceUnitCode) {
        // 缺追溯单位：按未扫默认拆零
        if (isEmpty(traceUnitCode)) {
            return resolveTrdnFlagUnscannedDefault();
        }
        // 缺操作单位：无法比阶，按未扫默认拆零
        if (isEmpty(operationUnitCode)) {
            return resolveTrdnFlagUnscannedDefault();
        }
        // 操作单位与追溯码单位一致：整包装
        if (operationUnitCode.trim().equals(traceUnitCode.trim())) {
            return TrdnFlag.WHOLE.getCode();
        }
        return TrdnFlag.SPLIT.getCode();
    }

    /**
     * 按各码实扣拆零判定拆零标志（DD §4.2 已扫口径）。
     *
     * <p>未扫（码空）→ {@code 1}。已扫：任一码实扣不能被每码满量整除 → {@code 1}；全部满码整出 → {@code 0}。</p>
     *
     * @param scannedTraceCodes   已扫码集合；空/null 视为未扫
     * @param perCodeSplitDeducts 与扫码对应的各码实扣拆零量；可与码同序或仅非空量列表
     * @param perTraceSplitQty    1 个贴码单位对应拆零量；可空时回落单位比较
     * @param operationUnitCode   操作单位（辅助回落）
     * @param traceUnitCode       追溯单位（辅助回落）
     * @return 0 整包装；1 拆零
     */
    public static String resolveTrdnFlagFromActualDeducts(
        Collection<String> scannedTraceCodes,
        Collection<BigDecimal> perCodeSplitDeducts,
        BigDecimal perTraceSplitQty,
        String operationUnitCode,
        String traceUnitCode
    ) {
        boolean hasCode = false;
        // 扫码集合非空时扫描是否存在有效码
        if (scannedTraceCodes != null) {
            for (String code : scannedTraceCodes) {
                // 非空白码视为已扫
                if (!isEmpty(code) && !code.trim().isEmpty()) {
                    hasCode = true;
                    break;
                }
            }
        }
        // 未扫：默认拆零 1
        if (!hasCode) {
            return resolveTrdnFlagUnscannedDefault();
        }
        // 有码但无实扣量：回落单位/余数辅助
        if (perCodeSplitDeducts == null || perCodeSplitDeducts.isEmpty()) {
            return resolveTrdnFlag(operationUnitCode, traceUnitCode, null, perTraceSplitQty);
        }
        // 缺每码满量：无法按实扣余数判定，回落单位比较
        if (perTraceSplitQty == null || perTraceSplitQty.compareTo(BigDecimal.ZERO) <= 0) {
            return resolveTrdnFlag(operationUnitCode, traceUnitCode);
        }
        for (BigDecimal deduct : perCodeSplitDeducts) {
            // 实扣为空或非正：跳过该码
            if (deduct == null || deduct.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            // 任一码非满贴码单位整出 → 拆零
            if (deduct.remainder(perTraceSplitQty).compareTo(BigDecimal.ZERO) != 0) {
                return TrdnFlag.SPLIT.getCode();
            }
        }
        return TrdnFlag.WHOLE.getCode();
    }

    /**
     * 按拆零余数辅助判定（无每码实扣时）：操作拆零不能被每码拆零整除 → 拆零。
     *
     * <p>拆零量缺失时回落 {@link #resolveTrdnFlag(String, String)}。未扫场景请直接用
     * {@link #resolveTrdnFlagUnscannedDefault()} 或 {@link #resolveTrdnFlagFromActualDeducts}。</p>
     *
     * @param operationUnitCode 操作单位
     * @param traceUnitCode     追溯码单位
     * @param operationSplitQty 本次操作拆零量；可空
     * @param perTraceSplitQty  1 个追溯码单位对应拆零量；可空
     * @return 0 整包装；1 拆零
     */
    public static String resolveTrdnFlag(
        String operationUnitCode,
        String traceUnitCode,
        BigDecimal operationSplitQty,
        BigDecimal perTraceSplitQty
    ) {
        // 操作拆零与每码拆零均有效：用余数辅助判定整包装/拆零
        if (operationSplitQty != null && perTraceSplitQty != null
            && operationSplitQty.compareTo(BigDecimal.ZERO) > 0
            && perTraceSplitQty.compareTo(BigDecimal.ZERO) > 0) {
            // 余数非 0：未用满整码 → 拆零标记
            if (operationSplitQty.remainder(perTraceSplitQty).compareTo(BigDecimal.ZERO) != 0) {
                return TrdnFlag.SPLIT.getCode();
            }
            return TrdnFlag.WHOLE.getCode();
        }
        return resolveTrdnFlag(operationUnitCode, traceUnitCode);
    }

    /**
     * 判断品种分类编码是否命中配置的白名单目录类型。
     *
     * @param categoryCode    品种目录分类编码
     * @param configuredTypes 配置的目录类型白名单
     * @return 命中白名单时 {@code true}
     */
    private static boolean matchesCatalogType(String categoryCode, List<String> configuredTypes) {
        // 分类或白名单为空：不命中
        if (isEmpty(categoryCode) || configuredTypes == null || configuredTypes.isEmpty()) {
            return false;
        }
        String normalized = categoryCode.trim();
        for (String configured : configuredTypes) {
            // 忽略大小写匹配目录类型
            if (configured != null && normalized.equalsIgnoreCase(configured.trim())) {
                return true;
            }
        }
        return false;
    }
}
