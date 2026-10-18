/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.pricing;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.zxplat.application.shared.product.ChargeItemProductLookupHelper;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.framework.core.application.util.StringUtils;

/**
 * 报表读侧：ChargeItem 包装倍数、按操作单位计行进价、展示单价对齐。
 *
 * <p><strong>行额口径（BR-MON-PKG-001，禁止踩雷）</strong>：
 * <ul>
 *   <li>操作单位已是包装：{@code 包装价 × 包装数量}，<strong>禁止</strong>折成拆零量再算；</li>
 *   <li>操作单位是拆零最小单位：满包×包装价 + 零头（{@code 包装价×余量/ratio} 单步）；
 *       <strong>禁止</strong>包装价÷ratio 得拆零价再×数量。</li>
 * </ul>
 * 展示用拆零单价仅可展示，禁止再乘数量算行额。</p>
 */
@Component
public class ChargeItemPackageUnitRatioSupport {

    /**
     * 由已加载的产品展示 Map 解析 chargeItemId → packageUnitRatio。
     *
     * @param chargeItems           收费明细
     * @param productByCompositeKey 复合键 → 产品展示；可空
     * @return chargeItemId → 包装倍数；无法解析时跳过该键
     */
    public Map<Long, BigDecimal> mapPackageUnitRatioByChargeItemId(
        Collection<ChargeItem> chargeItems,
        Map<String, ProductDefinitionDisplayDto> productByCompositeKey) {
        Map<Long, BigDecimal> ratioByChargeItemId = new LinkedHashMap<>();
        if (chargeItems == null || chargeItems.isEmpty()) {
            return ratioByChargeItemId;
        }
        Map<String, ProductDefinitionDisplayDto> productMap = productByCompositeKey;
        if (productMap == null) {
            productMap = Map.of();
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            ProductDefinitionDisplayDto product = this.resolveProduct(chargeItem, productMap);
            if (product == null || product.getPackageUnitRatio() == null) {
                continue;
            }
            // 仅收录正倍数；无效倍数不进 Map，调用方退化为 lineTotal
            if (product.getPackageUnitRatio().compareTo(BigDecimal.ZERO) > 0) {
                ratioByChargeItemId.put(chargeItem.getId(), product.getPackageUnitRatio());
            }
        }
        return ratioByChargeItemId;
    }

    /**
     * 按收费操作单位计算行进价总额（包装价锚；禁止包装价→拆零价再×数量）。
     *
     * <p>委托 {@link #computePurchaseLineAmount(BigDecimal, BigDecimal, BigDecimal, String,
     * ProductDefinitionDisplayDto)}。</p>
     *
     * @param packageUnitPrice 包装单位进价单价
     * @param packageUnitRatio 1 包装含拆零数；可空
     * @param chargeItem       收费明细（数量 + quantity_unit）
     * @param product          产品展示（包装/拆零单位）；可空
     * @return 行进价总额；单价空则 null
     */
    public BigDecimal computePurchaseLineAmount(
        BigDecimal packageUnitPrice,
        BigDecimal packageUnitRatio,
        ChargeItem chargeItem,
        ProductDefinitionDisplayDto product) {
        BigDecimal quantity = null;
        String quantityUnit = null;
        if (chargeItem != null) {
            quantity = chargeItem.getQuantityValue();
            quantityUnit = chargeItem.getQuantityUnit();
        }
        return this.computePurchaseLineAmount(
            packageUnitPrice, packageUnitRatio, quantity, quantityUnit, product);
    }

    /**
     * 按操作单位计算行进价总额（发药/日结等非 ChargeItem 锚点共用）。
     *
     * <p>分支：
     * <ul>
     *   <li>操作单位 = 包装单位：{@link MonetaryAmountSupport#lineTotal}（包装价×包装数量）；</li>
     *   <li>否则按拆零量：{@link MonetaryAmountSupport#computeSplitQuantityLineTotal}
     *       （满包×包装价 + 零头单步）。</li>
     * </ul>
     * 数量可为负（退药轧差）；禁止包装价÷ratio 得拆零价再×数量。</p>
     *
     * @param packageUnitPrice 包装单位进价单价
     * @param packageUnitRatio 1 包装含拆零数；可空
     * @param quantity         操作数量（与 quantityUnit 同阶；可负）
     * @param quantityUnit     操作单位码；可空
     * @param product          产品展示；可空
     * @return 行进价总额；单价空则 null
     */
    public BigDecimal computePurchaseLineAmount(
        BigDecimal packageUnitPrice,
        BigDecimal packageUnitRatio,
        BigDecimal quantity,
        String quantityUnit,
        ProductDefinitionDisplayDto product) {
        if (packageUnitPrice == null) {
            return null;
        }
        BigDecimal qty = quantity;
        if (qty == null) {
            // 无数量按 1 计行额
            qty = BigDecimal.ONE;
        }
        // 操作单位已是包装：直接包装价×包装数量，禁止折拆零
        if (this.isQuantityInPackageUnit(quantityUnit, product)) {
            return MonetaryAmountSupport.lineTotal(packageUnitPrice, qty);
        }
        // 操作单位按拆零解释：满包×包装价 + 零头（包装价×余量/ratio），禁止先拆零价再乘
        if (packageUnitRatio != null && packageUnitRatio.compareTo(BigDecimal.ZERO) > 0) {
            return MonetaryAmountSupport.computeSplitQuantityLineTotal(
                qty, packageUnitRatio, packageUnitPrice);
        }
        // 无倍数：退化为单价×数量
        return MonetaryAmountSupport.lineTotal(packageUnitPrice, qty);
    }

    /**
     * 判断 ChargeItem 数量单位是否为产品包装单位（且包装≠拆零）。
     *
     * @param chargeItem 收费明细
     * @param product    产品展示
     * @return true 表示按包装单位操作，行额直接包装价×数量
     */
    public boolean isQuantityInPackageUnit(ChargeItem chargeItem, ProductDefinitionDisplayDto product) {
        if (chargeItem == null) {
            return false;
        }
        return this.isQuantityInPackageUnit(chargeItem.getQuantityUnit(), product);
    }

    /**
     * 判断操作单位码是否为产品包装单位（且包装≠拆零）。
     *
     * @param quantityUnit 操作单位码
     * @param product      产品展示
     * @return true 表示按包装单位操作，行额直接包装价×数量
     */
    public boolean isQuantityInPackageUnit(String quantityUnit, ProductDefinitionDisplayDto product) {
        if (product == null) {
            return false;
        }
        String opUnit = StringUtils.trimToNull(quantityUnit);
        String packageUnit = StringUtils.trimToNull(product.getPackageUnitCode());
        if (opUnit == null || packageUnit == null) {
            return false;
        }
        if (!opUnit.equals(packageUnit)) {
            return false;
        }
        BigDecimal ratio = product.getPackageUnitRatio();
        // 倍数缺失或 ≤1：包装与拆零同阶，走 lineTotal 即可，不标「包装路径」
        if (ratio == null || ratio.compareTo(BigDecimal.ONE) <= 0) {
            return false;
        }
        return true;
    }

    /**
     * 包装单位进价单价 → 与收费数量单位对齐的展示进价单价（仅展示；禁止再乘数量算行额）。
     *
     * @param packageUnitPrice 包装单位进价单价
     * @param packageUnitRatio 1 包装含拆零数
     * @param chargeItem       收费明细（取 quantity_unit）
     * @param product          产品展示
     * @return 展示进价单价；入参单价空则 null
     */
    public BigDecimal toChargeAlignedPurchaseDisplayUnitPrice(
        BigDecimal packageUnitPrice,
        BigDecimal packageUnitRatio,
        ChargeItem chargeItem,
        ProductDefinitionDisplayDto product) {
        String quantityUnit = null;
        if (chargeItem != null) {
            quantityUnit = chargeItem.getQuantityUnit();
        }
        return this.toAlignedPurchaseDisplayUnitPrice(
            packageUnitPrice, packageUnitRatio, quantityUnit, product);
    }

    /**
     * 包装单位进价单价 → 与操作单位对齐的展示进价单价（仅展示；禁止再乘数量算行额）。
     *
     * <p>包装操作单位回包装价；拆零操作单位才折展示拆零价。</p>
     *
     * @param packageUnitPrice 包装单位进价单价
     * @param packageUnitRatio 1 包装含拆零数
     * @param quantityUnit     操作单位码
     * @param product          产品展示
     * @return 展示进价单价；入参单价空则 null
     */
    public BigDecimal toAlignedPurchaseDisplayUnitPrice(
        BigDecimal packageUnitPrice,
        BigDecimal packageUnitRatio,
        String quantityUnit,
        ProductDefinitionDisplayDto product) {
        if (packageUnitPrice == null) {
            return null;
        }
        // 包装操作单位：展示包装进价（与数量同阶）
        if (this.isQuantityInPackageUnit(quantityUnit, product)) {
            return MonetaryAmountSupport.scaleCalc(packageUnitPrice);
        }
        return this.toSplitUnitDisplayPrice(packageUnitPrice, packageUnitRatio);
    }

    /**
     * 包装单位进价单价 → 拆零单位展示单价（仅展示；禁止再乘数量算行额）。
     *
     * <p>公式复用 {@link MonetaryAmountSupport#convertLineUnitPriceToPackageUnitPrice}：
     * {@code P_split = P_pkg × 1 / R_pkg}。</p>
     *
     * @param packageUnitPrice 包装单位进价单价
     * @param packageUnitRatio 1 包装含拆零数；空或 ≤0 时原样刻度返回
     * @return 拆零展示单价；入参单价空则 null
     */
    public BigDecimal toSplitUnitDisplayPrice(BigDecimal packageUnitPrice, BigDecimal packageUnitRatio) {
        if (packageUnitPrice == null) {
            return null;
        }
        if (packageUnitRatio == null || packageUnitRatio.compareTo(BigDecimal.ZERO) <= 0) {
            return MonetaryAmountSupport.scaleCalc(packageUnitPrice);
        }
        if (packageUnitRatio.compareTo(BigDecimal.ONE) == 0) {
            return MonetaryAmountSupport.scaleCalc(packageUnitPrice);
        }
        // 包装价 → 拆零展示价：目标倍数=1（仅展示）
        return MonetaryAmountSupport.convertLineUnitPriceToPackageUnitPrice(
            packageUnitPrice, packageUnitRatio, BigDecimal.ONE);
    }

    /**
     * 从产品 Map 解析 ChargeItem 对应产品展示。
     *
     * @param chargeItem 收费明细
     * @param productMap 复合键 → 产品
     * @return 产品展示；缺锚点或无键时 null
     */
    public ProductDefinitionDisplayDto resolveProduct(
        ChargeItem chargeItem,
        Map<String, ProductDefinitionDisplayDto> productMap) {
        if (chargeItem == null || productMap == null || productMap.isEmpty()) {
            return null;
        }
        ProductDefinitionBatchLookup lookup =
            ChargeItemProductLookupHelper.buildLookupFromChargeItem(chargeItem);
        if (lookup == null
            || StringUtils.isEmpty(lookup.getProductTableName())
            || lookup.getProductDefinitionId() == null) {
            return null;
        }
        String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
            lookup.getProductTableName(), lookup.getProductDefinitionId());
        if (compositeKey == null) {
            return null;
        }
        return productMap.get(compositeKey);
    }
}
