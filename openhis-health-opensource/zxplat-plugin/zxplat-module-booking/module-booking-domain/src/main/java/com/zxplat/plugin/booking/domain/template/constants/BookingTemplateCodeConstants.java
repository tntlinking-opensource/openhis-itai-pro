/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.template.constants;

/**
 * 排班模板与放号策略自动编码前缀（用户未手填时由领域层生成唯一编码）。
 *
 * <p>前缀用于运维在列表中区分模板类型；后缀由 {@link com.zxplat.plugin.booking.domain.template.support.BookingTemplateCodeSupport} 生成。</p>
 */
public final class BookingTemplateCodeConstants {

    /** T1 放号时段模板编码前缀。 */
    public static final String PREFIX_SLOT_PROVISION = "T1-";

    /** T2 日排班模板编码前缀。 */
    public static final String PREFIX_DAY_SCHEDULE = "T2-";

    /** TW 周排班模板编码前缀。 */
    public static final String PREFIX_WEEK_SCHEDULE = "TW-";

    /** 放号策略编码前缀。 */
    public static final String PREFIX_RELEASE_POLICY = "RP-";

    /** 工具类禁止实例化。 */
    private BookingTemplateCodeConstants() {
    }
}
