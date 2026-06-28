/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.share.utils;

import com.openhis.health.domain.share.constants.DailySettlementSharedConstants;
import org.apache.commons.lang3.StringUtils;

/**
 * 财务类别 {@code fin_type_code} 与目录短码之间的归一化（药品 {@code medication_category_code} 等）。
 *
 * <p>供日结汇总、费用明细、批号定价写路径共用，避免各模块重复映射。</p>
 */
public final class FinTypeCodeMappingSupport {

    /**
     * 禁止实例化：仅提供静态映射方法。
     */
    private FinTypeCodeMappingSupport() {
    }

    /**
     * 将定价行 {@code type_code} 或药品目录 {@code category_code} 归一化为 {@code fin_type_code}。
     *
     * @param categoryCode 定价行 type_code 或 medication_category_code 短码
     * @return fin_type_code；空白时返回 {@code null}
     */
    public static String normalizeCategoryCodeToFinType(String categoryCode) {
        if (StringUtils.isBlank(categoryCode)) {
            return null;
        }
        String normalized = categoryCode.trim();
        if (DailySettlementSharedConstants.MEDICATION_CATEGORY_WESTERN_CODE.equals(normalized)) {
            return DailySettlementSharedConstants.WESTERN_MEDICINE_FIN_TYPE_CODE;
        }
        if (DailySettlementSharedConstants.MEDICATION_CATEGORY_PATENT_CHINESE_CODE.equals(normalized)) {
            return DailySettlementSharedConstants.CHINESE_PATENT_MEDICINE_FIN_TYPE_CODE;
        }
        if (DailySettlementSharedConstants.MEDICATION_CATEGORY_HERBAL_CODE.equals(normalized)) {
            return DailySettlementSharedConstants.CHINESE_HERBAL_MEDICINE_FIN_TYPE_CODE;
        }
        if (DailySettlementSharedConstants.MEDICATION_CATEGORY_PURCHASED_CODE.equals(normalized)) {
            return DailySettlementSharedConstants.PURCHASED_MEDICINE_FIN_TYPE_CODE;
        }
        return normalized;
    }
}
