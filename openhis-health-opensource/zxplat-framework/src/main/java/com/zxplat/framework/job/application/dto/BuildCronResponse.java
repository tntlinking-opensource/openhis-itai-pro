/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.job.application.dto;

import lombok.Data;

/**
 * 生成 Cron 表达式结果。
 */
@Data
public class BuildCronResponse {

    /** Cron 表达式。 */
    private String cronExpression;

    /** 中文描述。 */
    private String description;
}
