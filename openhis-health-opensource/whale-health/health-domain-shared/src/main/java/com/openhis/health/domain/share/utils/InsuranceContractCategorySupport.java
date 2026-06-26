/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.share.utils;

import com.openhis.health.domain.share.enums.ChargeTypeCategory;
import org.apache.commons.lang3.StringUtils;

/**
 * 费用性质是否为医保/公费类（与 {@link ChargeTypeCategory} 数值码对齐）。
 */
public final class InsuranceContractCategorySupport {

    private InsuranceContractCategorySupport() {
    }

    public static boolean isInsuranceCategoryEnum(String categoryEnum) {
        ChargeTypeCategory parsed = parseChargeTypeCategory(categoryEnum);
        return parsed == ChargeTypeCategory.PROVINCIAL_INSURANCE
            || parsed == ChargeTypeCategory.MUNICIPAL_INSURANCE;
    }

    public static boolean isPublicChargeCategoryEnum(String categoryEnum) {
        return parseChargeTypeCategory(categoryEnum) == ChargeTypeCategory.PUBLIC_PAY;
    }

    public static boolean isInsuranceContract(Boolean ybFlag, String categoryEnum) {
        if (Boolean.TRUE.equals(ybFlag)) {
            return true;
        }
        return isInsuranceCategoryEnum(categoryEnum);
    }

    private static ChargeTypeCategory parseChargeTypeCategory(String categoryEnum) {
        if (StringUtils.isBlank(categoryEnum)) {
            return null;
        }
        return EnumUtil.fromCode(ChargeTypeCategory.class, categoryEnum.trim());
    }
}
