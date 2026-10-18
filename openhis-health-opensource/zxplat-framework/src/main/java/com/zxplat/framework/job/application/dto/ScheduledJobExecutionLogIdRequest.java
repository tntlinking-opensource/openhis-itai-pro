/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.job.application.dto;

import lombok.Data;

/**
 * 按 ID 查询执行日志。
 */
@Data
public class ScheduledJobExecutionLogIdRequest {

    /** 日志主键。 */
    private Long id;
}
