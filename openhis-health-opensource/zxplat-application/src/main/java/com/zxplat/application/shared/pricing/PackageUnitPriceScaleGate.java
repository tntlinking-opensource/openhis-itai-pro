/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.pricing;

import com.zxplat.domain.share.constants.DailySettlementSharedConstants;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.application.util.StringUtils;

import java.math.BigDecimal;

/**
 * 包装单位进/售价<strong>维护位数</strong>闸门（目录维护、进货同意、期初同意共用）。
 *
 * <p>口径（与 monetary-amount-contract / <strong>BR-MON-001</strong> 对齐）：
 * 维护侧售价（含批号售价、目录 SALE/LIMIT）本身 ≤2 位；进价非中药 ≤2 位、中药饮片 ≤3 位。
 * <strong>不</strong>因「包装价÷包装比除不尽」拒绝维护或结算——除不尽由收费侧售价 2 位 HALF_UP、
 * 行额满包+零头（BR-MON-PKG-001）消化。</p>
 *
 * <p>进货/期初核心写批号价时：进价按中药/西药刻度落 {@code LOT_NUMBER_COST}；
 * {@code LOT_NUMBER_PRICE} <strong>一律 ≤2 位</strong>（中药三位进价时售价占位先 HALF_UP 到 2 位，
 * 中草药加价切面再覆盖为进价×1.25 的两位售价）。<strong>禁止</strong>把三位进价原样写入售价槽。</p>
 */
public final class PackageUnitPriceScaleGate {

    private PackageUnitPriceScaleGate() {
    }

    /**
     * 进货/期初核心路径解析后的包装进/售价（售价已保证 ≤2 位）。
     *
     * @param purchasePackagePrice 写入 {@code LOT_NUMBER_COST} / 目录 PURCHASE 的包装进价
     * @param salePackagePrice     写入 {@code LOT_NUMBER_PRICE} / 目录 SALE 占位的包装售价（≤2 位）
     */
    public record LotWritePricePair(BigDecimal purchasePackagePrice, BigDecimal salePackagePrice) {
    }

    /**
     * 校验包装售价刻度（≤2 位）。
     *
     * @param packageSalePrice 元/包装单位售价
     * @param errPrefix        异常前缀
     * @throws ValidationException 超 2 位
     */
    public static void assertSalePackagePriceScale(BigDecimal packageSalePrice, String errPrefix) {
        if (packageSalePrice == null) {
            return;
        }
        if (!MonetaryAmountSupport.isSaleUnitPriceScaleValid(packageSalePrice)) {
            throw new ValidationException(errPrefix + "：售价最多 2 位小数，当前="
                + packageSalePrice.toPlainString());
        }
    }

    /**
     * 校验包装进价刻度（非中药 ≤2；中药饮片 ≤3）。
     *
     * @param packagePurchasePrice 元/包装单位进价
     * @param tcmHerbal            是否中药饮片
     * @param errPrefix            异常前缀
     * @throws ValidationException 超允许位数
     */
    public static void assertPurchasePackagePriceScale(
        BigDecimal packagePurchasePrice,
        boolean tcmHerbal,
        String errPrefix) {
        if (packagePurchasePrice == null) {
            return;
        }
        if (!MonetaryAmountSupport.isPurchaseUnitPriceScaleValid(packagePurchasePrice, tcmHerbal)) {
            if (tcmHerbal) {
                throw new ValidationException(errPrefix + "：中药进价最多 3 位小数，当前="
                    + packagePurchasePrice.toPlainString());
            }
            throw new ValidationException(errPrefix + "：进价最多 2 位小数，当前="
                + packagePurchasePrice.toPlainString());
        }
    }

    /**
     * 进货/期初核心：校验原始包装进价位数，并拆成「进价刻度 + 售价占位（恒 ≤2 位）」。
     *
     * <p>中药进价三位时，售价占位 = 进价 HALF_UP 到 2 位（仅占位；加价切面同事务覆盖为真实两位售价）。
     * 西药/耗材进售占位同值且均为 2 位。</p>
     *
     * @param product                   产品展示（判中药分类；可空按非中药）
     * @param rawPackagePurchasePrice   行上包装进价（未按进价刻度归一前）
     * @param errPrefix                 异常前缀
     * @return 进/售写价对；raw 为空时返回 null
     * @throws ValidationException 进价超允许位数
     */
    public static LotWritePricePair resolveAndAssertCoreLotWritePrices(
        ProductDefinitionDisplayDto product,
        BigDecimal rawPackagePurchasePrice,
        String errPrefix) {
        if (rawPackagePurchasePrice == null) {
            return null;
        }
        boolean tcmHerbal = isTcmHerbal(product);
        // Gate：原始进价不得超过维护上限（中药 3 / 其余 2）
        assertPurchasePackagePriceScale(rawPackagePurchasePrice, tcmHerbal, errPrefix);
        BigDecimal purchase = MonetaryAmountSupport.scalePurchaseUnitPrice(
            rawPackagePurchasePrice, tcmHerbal);
        // 售价槽禁止三位：占位一律 2 位 HALF_UP（中药真实售价由加价切面再写）
        BigDecimal sale = MonetaryAmountSupport.scaleSaleUnitPrice(rawPackagePurchasePrice);
        assertSalePackagePriceScale(sale, errPrefix);
        return new LotWritePricePair(purchase, sale);
    }

    /**
     * 进货/期初/加价写批号价前：校验包装进/售价维护位数（不做可拆整除拒绝）。
     *
     * @param product              产品展示（可空则仅按非中药进价口径）
     * @param packagePurchasePrice 本行包装进价
     * @param packageSalePrice     本行包装售价（须已是 ≤2 位的真实或占位售价）
     * @param errPrefix            异常前缀
     * @throws ValidationException 维护位数不合规
     */
    public static void assertForProductLotWrite(
        ProductDefinitionDisplayDto product,
        BigDecimal packagePurchasePrice,
        BigDecimal packageSalePrice,
        String errPrefix) {
        boolean tcmHerbal = isTcmHerbal(product);
        // Gate：包装进价位数
        assertPurchasePackagePriceScale(packagePurchasePrice, tcmHerbal, errPrefix);
        // Gate：包装售价位数（业务售价永不超过 2 位，含中药）
        assertSalePackagePriceScale(packageSalePrice, errPrefix);
    }

    /**
     * 是否中药饮片（{@code medication_category_code=4}）。
     *
     * @param product 产品展示
     * @return true 表示中草药分类
     */
    public static boolean isTcmHerbal(ProductDefinitionDisplayDto product) {
        if (product == null) {
            return false;
        }
        return DailySettlementSharedConstants.MEDICATION_CATEGORY_HERBAL_CODE
            .equals(StringUtils.trimToEmpty(product.getCategoryCode()));
    }
}
