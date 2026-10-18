/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.pricing;

import com.zxplat.domain.administration.chargeitemdefinition.command.SaveAllChargeItemDefinitionsCommand;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.PriceType;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 应用层共享的收费定价命令装配器。
 *
 * <p>职责：把页面/应用层的金额字段转换为收费定义批量写命令项；金额按元写入 {@code price_value}。
 * 售价类（SALE/LIMIT/LOT_NUMBER_PRICE）落 2 位；进价类默认 2 位（中药饮片进价由调用方先刻到 3 位再传入）。</p>
 */
public final class ChargePricingCommandAssembler {

    /**
     * 禁止实例化：仅提供静态装配方法。
     */
    private ChargePricingCommandAssembler() {
    }

    /**
     * 金额非空则追加一条定价行。
     *
     * @param items 批量命令条目列表
     * @param instanceTable 实例表名
     * @param instanceId 实例主键
     * @param typeCode 财务类型编码（fin_type_code，必填；由领域层校验）
     * @param ybType 医保类别
     * @param priceTypeCode 定价类型
     * @param yuanPrice 元；{@code null} 跳过
     * @param chargeName 收费项展示名称；可空
     */
    public static void appendPriceLineIfPresent(
        List<SaveAllChargeItemDefinitionsCommand.Item> items,
        String instanceTable,
        Long instanceId,
        String typeCode,
        String ybType,
        String priceTypeCode,
        BigDecimal yuanPrice,
        String chargeName) {
        if (yuanPrice == null) {
            return;
        }
        // Gate：有金额必有 fin_type，禁止落库空 type_code（BR-CHGDEF-WR-002）
        if (StringUtils.isEmpty(typeCode)) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
                "定价行缺少财务类型 type_code，instanceTable=" + instanceTable + ", instanceId=" + instanceId);
        }
        SaveAllChargeItemDefinitionsCommand.Item item = new SaveAllChargeItemDefinitionsCommand.Item();
        item.setInstanceTable(instanceTable);
        item.setInstanceId(instanceId);
        item.setTypeCode(typeCode.trim());
        item.setYbType(ybType);
        item.setPriceTypeCode(priceTypeCode);
        item.setChargeName(chargeName);
        // Gate：售价类强制 2 位；进价类亦按 2 位写入（中药 3 位须调用方预先 scalePurchaseUnitPrice）
        item.setPriceValue(scalePriceValueForType(priceTypeCode, yuanPrice));
        items.add(item);
    }

    /**
     * 按价类选择落库刻度：售价语义强制 2 位；进价类保持调用方已校验刻度（西药 2 / 中药 3），写入计算标度。
     *
     * @param priceTypeCode 价类 code
     * @param yuanPrice     元金额
     * @return 刻度后金额
     */
    private static BigDecimal scalePriceValueForType(String priceTypeCode, BigDecimal yuanPrice) {
        String code = StringUtils.trimToEmpty(priceTypeCode);
        if (PriceType.SALE.getCode().equals(code)
            || PriceType.LIMIT.getCode().equals(code)
            || PriceType.LOT_NUMBER_PRICE.getCode().equals(code)) {
            return MonetaryAmountSupport.scaleSaleUnitPrice(yuanPrice);
        }
        // 进价：目录侧已按西药2位/中药3位闸过，此处仅规范到计算标度，禁止再压成售价两位误伤中药
        return MonetaryAmountSupport.scaleCalc(yuanPrice);
    }
}
