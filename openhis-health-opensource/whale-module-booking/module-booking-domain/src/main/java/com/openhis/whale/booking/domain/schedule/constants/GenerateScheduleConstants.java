/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.schedule.constants;

/**
 * 从模板批量生成正式排班（Schedule）的领域常量。
 */
public final class GenerateScheduleConstants {

    /** 单次「生成正式排班」允许的最大日历天数（含起止日），防止一次落库过多草稿。 */
    public static final int MAX_GENERATE_DAYS = 62;

    /** 工具类禁止实例化。 */
    private GenerateScheduleConstants() {
    }
}
