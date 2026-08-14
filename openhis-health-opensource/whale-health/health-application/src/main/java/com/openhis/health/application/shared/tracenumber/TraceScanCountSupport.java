/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.tracenumber;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 追溯码应扫个数：<strong>仅进货</strong>硬 Gate 用 {@link #requireExactInboundCodeCount}；
 * 期初与消耗/流转侧不作个数硬拒（DD：不因追溯码卡主流程，进货除外）。
 *
 * <p>操作单位与追溯码单位相同时，直接用操作数量，不绕拆零；单位不同时以拆零为公比。
 * 半盒入库（如 1 盒=10 支、实入 5 支）应扫 1 码，主档剩余记实入拆零。</p>
 */
public final class TraceScanCountSupport {

    private TraceScanCountSupport() {
    }

    /**
     * 进货 Gate：应扫码数 = ceil(行拆零 / 每码拆零)，不足一整码时至少 1（与出库同口径）。
     *
     * <p>方法名保留历史 {@code requireExact*}；实现已改为 ceil，不再要求行拆零被每码拆零整除。
     * <strong>期初请勿再调用本方法做硬拒</strong>（已改为扫码可选）。</p>
     *
     * @param conversionSource 药典单位换算源（含目录追溯单位与倍数）
     * @param traceUnitCode    追溯码所贴单位（来自药典；写入主档）
     * @param lineSplitQty     本行入库拆零总量（库存真源）
     * @return 应扫码数（正整数）
     */
    public static int requireExactInboundCodeCount(
        MedicationProductUnitConversionSource conversionSource,
        String traceUnitCode,
        BigDecimal lineSplitQty
    ) {
        if (lineSplitQty == null || lineSplitQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "入库拆零数量无效，无法计算应扫码数");
        }
        if (StringUtils.isEmpty(traceUnitCode)) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "追溯码单位为空，无法计算应扫码数");
        }
        BigDecimal perCodeSplit = resolvePerCodeSplit(conversionSource, traceUnitCode);
        BigDecimal ratio = lineSplitQty.divide(perCodeSplit, 6, RoundingMode.HALF_UP);
        return ceilToAtLeastOne(ratio);
    }

    /**
     * 消耗侧单行可绑码个数硬上限：{@code ceil(操作量)}。
     *
     * <p>开 2 支→2、开 1 盒→1。码倍/拆零<strong>不</strong>参与个数（只用于盖住判定）。</p>
     *
     * @param operationQuantity 操作数量（与发药/出库操作单位同阶）
     * @return ≥1 的整数上限
     */
    public static int resolveOutboundMaxCodeCount(BigDecimal operationQuantity) {
        if (operationQuantity == null || operationQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "操作数量无效，无法计算最多可扫码数");
        }
        return ceilToAtLeastOne(operationQuantity);
    }

    /**
     * 出库/发药：应扫码数 = ceil(操作拆零 / 每码拆零)，不足一整码时至少 1。
     *
     * <p>操作单位已等于追溯码单位时，直接取操作数量向上取整。
     * <strong>仅软提示</strong>；硬上限见 {@link #resolveOutboundMaxCodeCount}。</p>
     *
     * @param conversionSource  药典单位换算源
     * @param operationUnitCode 操作单位
     * @param operationQuantity 操作数量（与操作单位同阶；库房界面由拆零换出）
     * @param traceUnitCode     主档追溯码单位
     * @return 应扫码数（≥1）
     */
    public static int resolveOutboundRequiredCodeCount(
        MedicationProductUnitConversionSource conversionSource,
        String operationUnitCode,
        BigDecimal operationQuantity,
        String traceUnitCode
    ) {
        if (operationQuantity == null || operationQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "操作数量无效，无法计算应扫码数");
        }
        if (StringUtils.isEmpty(traceUnitCode)) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "追溯码单位为空，无法计算应扫码数");
        }
        String traceUnit = traceUnitCode.trim();
        // 操作单位与追溯码单位同阶：直接用操作数量，避免无谓绕拆零
        if (StringUtils.isNotEmpty(operationUnitCode)
            && operationUnitCode.trim().equals(traceUnit)) {
            return ceilToAtLeastOne(operationQuantity);
        }
        BigDecimal opSplit = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
            conversionSource,
            operationUnitCode,
            operationQuantity
        );
        BigDecimal perCodeSplit = resolvePerCodeSplit(conversionSource, traceUnit);
        BigDecimal ratio = opSplit.divide(perCodeSplit, 6, RoundingMode.HALF_UP);
        return ceilToAtLeastOne(ratio);
    }

    /**
     * 医保拆零标记：本次操作拆零量能否被「每码拆零」整除。
     *
     * <p>整除 → 整包装（0）；有余数（如盒码发 3 片）→ 拆零（1）。单位相等且数量为整数时亦为整包装。</p>
     *
     * @param operationSplitQty 本次操作拆零量
     * @param perTraceSplitQty  1 个追溯码单位对应的拆零量
     * @return {@code true} 表示应打拆零标记
     */
    public static boolean isSplitDispenseByRemainder(
        BigDecimal operationSplitQty,
        BigDecimal perTraceSplitQty
    ) {
        if (operationSplitQty == null || perTraceSplitQty == null) {
            return false;
        }
        if (operationSplitQty.compareTo(BigDecimal.ZERO) <= 0 || perTraceSplitQty.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return operationSplitQty.remainder(perTraceSplitQty).compareTo(BigDecimal.ZERO) != 0;
    }

    /**
     * 1 个追溯码单位换算为拆零量。
     *
     * @param conversionSource 换算源（含目录 {@code traceUnitRatio}）
     * @param traceUnitCode    追溯码单位
     * @return 每码拆零量
     */
    public static BigDecimal resolvePerCodeSplit(
        MedicationProductUnitConversionSource conversionSource,
        String traceUnitCode
    ) {
        if (StringUtils.isEmpty(traceUnitCode)) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "追溯码单位不能为空");
        }
        return MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
            conversionSource,
            traceUnitCode.trim(),
            BigDecimal.ONE
        );
    }

    /**
     * 向上取整且至少为 1。
     *
     * @param value 比值或数量
     * @return ≥1 的整数
     */
    private static int ceilToAtLeastOne(BigDecimal value) {
        int n = value.setScale(0, RoundingMode.CEILING).intValue();
        if (n < 1) {
            return 1;
        }
        return n;
    }
}
