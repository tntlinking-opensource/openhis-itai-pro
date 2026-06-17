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

package com.openhis.health.module.common.assembler.advice;

import com.openhis.health.module.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.share.constants.ChargeItemAmountScaleConstants;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 医嘱开立/执行新建 Charge：按请求 {@code quantity}/{@code unit_code} 与目录定价行单位槽比例换算金额。
 *
 * <p>对齐 **BR-ORD-016**、**BR-IP-EXE-026**、**DM-ADM-011 BR-CHG-008**：禁止默认「1 次」或复制模板整价；数量与单位须来自前端载荷或已落库请求字段。</p>
 *
 * <p>定价表 {@code price_value}（含 {@code SALE}、{@code LOT_NUMBER_PRICE} 等）均为<strong>每包装单位价</strong>（DM-ADM-010 §1.5）。
 * 换算<strong>仅以包装单位为锚</strong>，经 {@link ProductDefinitionCatalogUnitBandsAssembler#buildDoctorStationBands}
 * 写各槽后按业务 {@code unit_code} 取槽价；只允许<strong>包装价 → 目标单位价</strong>，禁止以拆零/默认开立单位为锚再往回算。</p>
 */
public final class ProductDefinitionOrderChargePricingSupport {

    private ProductDefinitionOrderChargePricingSupport() {
    }

    /**
     * 将定价行与业务数量/单位写入 Charge 命令项金额字段。
     *
     * @param item              待写入的命令项（可已含 {@code definitionId}）
     * @param productRow        产品定义读模型；用于解析计价单位槽
     * @param priceLine         定价行；不得为 {@code null}
     * @param quantity          业务计价数量（与医嘱请求 quantity 同语义）
     * @param quantityUnitCode  业务计价单位 code（与医嘱请求 unit_code 同语义）
     * @throws BusinessException 定价行、数量或单位缺失/非法
     */
    public static void applyOrderChargePricing(
        CreateChargeItemCommand.Item item,
        ProductDefinitionDisplayDto productRow,
        ChargeItemDefinition priceLine,
        BigDecimal quantity,
        String quantityUnitCode
    ) {
        if (priceLine == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_CHARGE_PRICE_LINE_REQUIRED);
        }
        if (item == null) {
            return;
        }
        if (productRow == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_CHARGE_PRODUCT_DEFINITION_REQUIRED);
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_CHARGE_QUANTITY_REQUIRED);
        }
        String unitCode = StringUtils.trimToNull(quantityUnitCode);
        if (unitCode == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_CHARGE_UNIT_CODE_REQUIRED);
        }
        if (item.getDefinitionId() == null) {
            item.setDefinitionId(priceLine.getId());
        }
        BigDecimal catalogPrice = priceLine.getPriceValue();
        if (catalogPrice == null) {
            catalogPrice = BigDecimal.ZERO;
        }
        int monetaryScale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE;
        // 定价行 price_value 一律按包装单位锚写入各槽，再按业务开立单位取槽单价
        String pricingUnitCode = ProductDefinitionCatalogUnitBandsAssembler.resolvePackageAnchoredPricingUnitCode(
            productRow, priceLine.getPriceTypeCode());
        if (ProductDefinitionCatalogUnitBandsAssembler.isPackagePersistedUnitPriceType(priceLine.getPriceTypeCode())
            && pricingUnitCode == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_CHARGE_PACKAGE_UNIT_CODE_REQUIRED);
        }
        List<ProductDefinitionCatalogUnitBandDto> bands =
            ProductDefinitionCatalogUnitBandsAssembler.buildDoctorStationBands(
                productRow, pricingUnitCode, catalogPrice);
        BigDecimal unitPrice = catalogPrice;
        BigDecimal bandUnitPrice = resolveUnitPriceFromBands(bands, unitCode);
        if (bandUnitPrice != null) {
            unitPrice = bandUnitPrice;
        } else {
            // 槽上未预写单价时，按包装锚与目标单位的 ratioToSplit 做包装→目标线性换算
            BigDecimal pricingRatio = ProductDefinitionCatalogUnitBandsAssembler.resolveRatioToSplitByUnitCode(
                bands, pricingUnitCode);
            BigDecimal targetRatio = ProductDefinitionCatalogUnitBandsAssembler.resolveRatioToSplitByUnitCode(
                bands, unitCode);
            if (pricingRatio != null && targetRatio != null && pricingRatio.compareTo(BigDecimal.ZERO) > 0) {
                unitPrice = catalogPrice.multiply(targetRatio)
                    .divide(pricingRatio, monetaryScale, RoundingMode.HALF_UP);
            }
        }
        BigDecimal scaledUnitPrice = unitPrice.setScale(monetaryScale, RoundingMode.HALF_UP);
        BigDecimal totalPrice = scaledUnitPrice.multiply(quantity).setScale(monetaryScale, RoundingMode.HALF_UP);
        item.setQuantityValue(quantity);
        item.setQuantityUnit(unitCode);
        item.setUnitPrice(scaledUnitPrice);
        item.setTotalPrice(totalPrice);
        item.setBaseAmount(totalPrice);
        item.setDiscountAmount(totalPrice);
        item.setSurchargeAmount(BigDecimal.ZERO);
    }

    /**
     * 在单位槽列表中按持久化单位 code 取已装配的 {@code unitPrice}。
     *
     * @param bands    已按包装锚装配的单位槽
     * @param unitCode 业务开立单位编码
     * @return 对应槽单价；未命中时为 {@code null}
     */
    private static BigDecimal resolveUnitPriceFromBands(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        String unitCode
    ) {
        if (bands == null || bands.isEmpty() || StringUtils.isBlank(unitCode)) {
            return null;
        }
        String normalized = unitCode.trim();
        for (ProductDefinitionCatalogUnitBandDto band : bands) {
            if (band == null || StringUtils.isBlank(band.getUnitCode())) {
                continue;
            }
            if (!normalized.equals(band.getUnitCode().trim())) {
                continue;
            }
            return band.getUnitPrice();
        }
        return null;
    }
}
