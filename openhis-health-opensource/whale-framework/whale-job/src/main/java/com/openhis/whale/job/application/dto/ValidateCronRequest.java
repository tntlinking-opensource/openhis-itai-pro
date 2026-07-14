/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.job.application.dto;

import lombok.Data;


/**
 * Cron 校验请求。
 */
@Data
public class ValidateCronRequest {

    /** Cron 表达式。 */
    private String cronExpression;
}
