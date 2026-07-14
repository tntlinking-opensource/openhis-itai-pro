/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.utils;

import com.openhis.health.domain.share.enums.ChargeTypeCategory;
import com.openhis.whale.core.application.util.StringUtils;

/**
 * 费用性质是否为医保/公费类（与 {@link ChargeTypeCategory} 数值码对齐）。
 */
public final class InsuranceContractCategorySupport {

    private InsuranceContractCategorySupport() {
    }

    /**
     * 判断费用性质编码是否属于医保类（省医保或市医保）。
     *
     * @param categoryEnum {@link ChargeTypeCategory#getCode()} 持久化串；空白时视为非医保
     * @return {@code true} 表示省/市医保
     */
    public static boolean isInsuranceCategoryEnum(String categoryEnum) {
        ChargeTypeCategory parsed = parseChargeTypeCategory(categoryEnum);
        return parsed == ChargeTypeCategory.PROVINCIAL_INSURANCE
            || parsed == ChargeTypeCategory.MUNICIPAL_INSURANCE;
    }

    /**
     * 判断费用性质编码是否为公费医疗。
     *
     * @param categoryEnum {@link ChargeTypeCategory#getCode()} 持久化串
     * @return {@code true} 表示 {@link ChargeTypeCategory#PUBLIC_PAY}
     */
    public static boolean isPublicChargeCategoryEnum(String categoryEnum) {
        return parseChargeTypeCategory(categoryEnum) == ChargeTypeCategory.PUBLIC_PAY;
    }

    /**
     * 综合医保标志与费用性质，判定是否按医保合同口径处理。
     *
     * <p>{@code ybFlag=true} 时直接视为医保；否则回退至 {@link #isInsuranceCategoryEnum(String)}。</p>
     *
     * @param ybFlag         业务侧医保标记（可为 {@code null}）
     * @param categoryEnum   费用性质编码
     * @return {@code true} 表示应按医保合同处理
     */
    public static boolean isInsuranceContract(Boolean ybFlag, String categoryEnum) {
        if (Boolean.TRUE.equals(ybFlag)) {
            return true;
        }
        return isInsuranceCategoryEnum(categoryEnum);
    }

    /**
     * 费别 category 字符串 → {@link ChargeTypeCategory}；空或非法码 null。
     *
     * @param categoryEnum 持久化费别码
     * @return 枚举或 null
     */
    private static ChargeTypeCategory parseChargeTypeCategory(String categoryEnum) {
        if (StringUtils.isEmpty(categoryEnum)) {
            return null;
        }
        return EnumUtil.fromCode(ChargeTypeCategory.class, categoryEnum.trim());
    }
}
