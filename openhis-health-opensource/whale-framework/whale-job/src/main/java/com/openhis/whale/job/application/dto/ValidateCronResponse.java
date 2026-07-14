/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.job.application.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cron 校验结果。
 */
@Data
public class ValidateCronResponse {

    /** 是否合法。 */
    private boolean valid;

    /** 非法时的错误信息。 */
    private String errorMessage;

    /** 中文描述。 */
    private String description;

    /** 下次执行时间。 */
    private LocalDateTime nextFireTime;

    /** 未来若干次执行时间（预览）。 */
    private List<LocalDateTime> nextFireTimes = new ArrayList<>();
}
