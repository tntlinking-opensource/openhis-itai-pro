/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.inboundreceipt;

import java.math.BigDecimal;

/**
 * 产品药库进货中草药自动加价字面量。
 *
 * <p>中草药分类码真源复用
 * {@link com.openhis.health.domain.share.constants.DailySettlementSharedConstants#MEDICATION_CATEGORY_HERBAL_CODE}
 * （与 {@code DoctorStationTcmConstants.HERBAL_CATEGORY_CODE} 同为 {@code "4"}），本类不重复落码。</p>
 */
public final class HerbalInboundReceiptConstants {

    /**
     * 调价审计原因全文：须与调价/财务审批列表排除条件精确相等，禁止进手工调价原因下拉。
     */
    public static final String AUTO_MARKUP_ADJUSTMENT_REASON = "中药自动调价";

    /**
     * 中草药批号售价加价倍率（进价 × 本值后第三位起直接舍弃）。
     */
    public static final BigDecimal HERBAL_MARKUP_RATE = new BigDecimal("1.25");

    /**
     * 禁止实例化：仅提供产品字面量。
     */
    private HerbalInboundReceiptConstants() {
    }
}
