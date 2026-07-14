/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.job.application.dto;

import lombok.Data;

/**
 * 通过可视化预设生成 Cron 表达式。
 */
@Data
public class BuildCronRequest {

    /** 预设类型 code。 */
    private String cronPresetType;

    /** 每 N 分钟。 */
    private Integer intervalMinutes;

    /** 分钟。 */
    private Integer minute;

    /** 小时。 */
    private Integer hour;

    /** 星期（1=周日）。 */
    private Integer dayOfWeek;

    /** 日。 */
    private Integer dayOfMonth;

    /** 自定义 Cron（CUSTOM 时必填）。 */
    private String customExpression;
}
