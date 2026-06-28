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

package com.openhis.health.module.common.assembler.pricing;

import com.openhis.health.domain.administration.chargeitemdefinition.command.SaveAllChargeItemDefinitionsCommand;
import com.openhis.health.domain.share.constants.ChargeItemAmountScaleConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 应用层共享的收费定价命令装配器。
 *
 * <p>职责：把页面/应用层的金额字段转换为收费定义批量写命令项；金额按元写入 {@code price_value}（库标度 6 位）。</p>
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
        if (StringUtils.isBlank(typeCode)) {
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
        item.setPriceValue(yuanPrice.setScale(ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE, RoundingMode.HALF_UP));
        items.add(item);
    }
}
