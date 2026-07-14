/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.inventoryitem.support;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;

import java.math.BigDecimal;

/**
 * 将药品发放数量从业务单位（医嘱/发放行 {@code unit_code}）换算为库存账面使用的<strong>最小单位（拆零单位）</strong>数量。
 *
 * <p>换算规则与产品定义中「相对拆零倍数」列一致；入参为 {@link MedicationProductUnitConversionSource}。</p>
 */
public final class MedicationProductQuantityToSplitUnitConverter {

    private MedicationProductQuantityToSplitUnitConverter() {
    }

    public static BigDecimal medicationQuantityToSplitUnit(
        MedicationProductUnitConversionSource product,
        String businessUnitCode,
        BigDecimal quantity
    ) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "换算入参数量必须大于 0");
        }
        if (product == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "缺少药品目录单位信息，无法把发放数量换算为库存最小单位");
        }
        if (StringUtils.isEmpty(businessUnitCode)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "发放单位编码为空，无法换算库存最小单位数量");
        }
        String unitTrimmed = businessUnitCode.trim();
        if (StringUtils.isNotEmpty(product.getSplitUnitCode())) {
            if (unitTrimmed.equals(product.getSplitUnitCode().trim())) {
                return quantity;
            }
        }
        if (StringUtils.isNotEmpty(product.getDoseUnitCode())) {
            if (unitTrimmed.equals(product.getDoseUnitCode().trim())) {
                return multiplyQuantityByRatio(quantity, product.getDoseUnitRatio(), "剂量单位");
            }
        }
        if (StringUtils.isNotEmpty(product.getPurchaseUnitCode())) {
            if (unitTrimmed.equals(product.getPurchaseUnitCode().trim())) {
                return multiplyQuantityByRatio(quantity, product.getPurchaseUnitRatio(), "采购单位");
            }
        }
        if (StringUtils.isNotEmpty(product.getStockUnitCode())) {
            if (unitTrimmed.equals(product.getStockUnitCode().trim())) {
                return multiplyQuantityByRatio(quantity, product.getStockUnitRatio(), "库存单位");
            }
        }
        if (StringUtils.isNotEmpty(product.getPackageUnitCode())) {
            if (unitTrimmed.equals(product.getPackageUnitCode().trim())) {
                return multiplyQuantityByRatio(quantity, product.getPackageUnitRatio(), "销售单位");
            }
        }
        throw new BusinessException(
            ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT,
            "发放单位不在目录拆零/剂量/采购/库存/销售五类单位内，无法换算库存最小单位数量");
    }

    /**
     * 数量 × 换算比；比缺失或非正数时抛 INVALID_ARGUMENT。
     *
     * @param quantity 原数量
     * @param ratio    换算比
     * @param label    错误提示中的单位标签
     * @return 乘积
     * @throws BusinessException 换算比非法
     */
    private static BigDecimal multiplyQuantityByRatio(BigDecimal quantity, BigDecimal ratio, String label) {
        if (ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT,
                label + "换算比缺失或不大于 0，无法换算库存最小单位数量");
        }
        return quantity.multiply(ratio);
    }
}
