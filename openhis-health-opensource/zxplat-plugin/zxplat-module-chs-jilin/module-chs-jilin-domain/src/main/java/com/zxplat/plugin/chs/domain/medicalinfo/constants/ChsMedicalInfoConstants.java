/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.medicalinfo.constants;

import java.time.format.DateTimeFormatter;

/** 5201–5206 医保信息查询业务常量 */
public final class ChsMedicalInfoConstants {

    /** 查询时间范围格式：yyyy-MM-dd HH:mm:ss */
    public static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 默认最大查询跨度（天） */
    public static final int DEFAULT_MAX_RANGE_DAYS = 92;

    private ChsMedicalInfoConstants() {
    }
}
