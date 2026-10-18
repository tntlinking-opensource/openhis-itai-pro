/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

import java.util.Set;

/**
 * 收费折扣配置共享常量（DM-CUS-016 / BR-CDC-002）。
 *
 * <p>三大目录表白名单复用 {@link TableNameConstants}，禁止在业务类再写同义字符串。</p>
 */
public interface ChargeDiscountConfigConstants {

    /**
     * 允许配置打折的产品定义表名（药 / 诊 / 材）。
     */
    Set<String> ALLOWED_PRODUCT_TABLES = Set.of(
        TableNameConstants.MED_MEDICATION_DEFINITION,
        TableNameConstants.WKF_ACTIVITY_DEFINITION,
        TableNameConstants.ADM_DEVICE_DEFINITION
    );

    /**
     * 分页筛选：仅大类行（{@code product_id} 空）。
     */
    String ROW_SHAPE_CATEGORY = "CATEGORY";

    /**
     * 分页筛选：仅单项行（{@code category_code} 空）。
     */
    String ROW_SHAPE_PRODUCT = "PRODUCT";

    /**
     * 批量命中 Map 键分隔符：{@code productTable + SEP + productId}。
     */
    String PRODUCT_KEY_SEPARATOR = "#";

    /**
     * IF-CDC-07 检索模式：大类（术语分类，不落库）。
     */
    String SEARCH_MODE_CATEGORY = "CATEGORY";

    /**
     * IF-CDC-07 检索模式：单项（目录项目，不落库）。
     */
    String SEARCH_MODE_ITEM = "ITEM";
}
