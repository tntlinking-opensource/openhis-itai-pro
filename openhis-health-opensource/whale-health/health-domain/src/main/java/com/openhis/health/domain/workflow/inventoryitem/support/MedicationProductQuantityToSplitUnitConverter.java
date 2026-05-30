/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.workflow.inventoryitem.support;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

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
        if (StringUtils.isBlank(businessUnitCode)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "发放单位编码为空，无法换算库存最小单位数量");
        }
        String unitTrimmed = businessUnitCode.trim();
        if (StringUtils.isNotBlank(product.getSplitUnitCode())) {
            if (unitTrimmed.equals(product.getSplitUnitCode().trim())) {
                return quantity;
            }
        }
        if (StringUtils.isNotBlank(product.getDoseUnitCode())) {
            if (unitTrimmed.equals(product.getDoseUnitCode().trim())) {
                return multiplyQuantityByRatio(quantity, product.getDoseUnitRatio(), "剂量单位");
            }
        }
        if (StringUtils.isNotBlank(product.getPurchaseUnitCode())) {
            if (unitTrimmed.equals(product.getPurchaseUnitCode().trim())) {
                return multiplyQuantityByRatio(quantity, product.getPurchaseUnitRatio(), "采购单位");
            }
        }
        if (StringUtils.isNotBlank(product.getStockUnitCode())) {
            if (unitTrimmed.equals(product.getStockUnitCode().trim())) {
                return multiplyQuantityByRatio(quantity, product.getStockUnitRatio(), "库存单位");
            }
        }
        if (StringUtils.isNotBlank(product.getPackageUnitCode())) {
            if (unitTrimmed.equals(product.getPackageUnitCode().trim())) {
                return multiplyQuantityByRatio(quantity, product.getPackageUnitRatio(), "销售单位");
            }
        }
        throw new BusinessException(
            ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT,
            "发放单位不在目录拆零/剂量/采购/库存/销售五类单位内，无法换算库存最小单位数量");
    }

    private static BigDecimal multiplyQuantityByRatio(BigDecimal quantity, BigDecimal ratio, String label) {
        if (ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT,
                label + "换算比缺失或不大于 0，无法换算库存最小单位数量");
        }
        return quantity.multiply(ratio);
    }
}
