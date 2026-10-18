/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

import java.math.BigDecimal;

/**
 * 库存期初中草药自动加价字面量（仅 IF-BAS-INV-OPN；不与药库进货常量类共用）。
 *
 * <p>分类码真源 {@link DailySettlementSharedConstants#MEDICATION_CATEGORY_HERBAL_CODE}，本接口不重复落码。
 * 审计原因全文须与调价列表排除条件精确相等。</p>
 */
public interface HerbalInventoryOpeningConstants {

    /**
     * 调价审计原因全文：须与调价/财务审批列表排除条件精确相等，禁止进手工调价原因下拉。
     */
    String AUTO_MARKUP_ADJUSTMENT_REASON = "中药自动调价";

    /**
     * 中草药批号售价加价倍率（进价 × 本值后第三位起直接舍弃）。
     */
    BigDecimal HERBAL_MARKUP_RATE = new BigDecimal("1.25");
}
