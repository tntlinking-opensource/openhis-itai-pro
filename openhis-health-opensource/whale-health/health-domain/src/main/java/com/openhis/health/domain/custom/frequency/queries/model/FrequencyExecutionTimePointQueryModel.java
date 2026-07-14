/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.frequency.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 频次执行时点查询模型。
 */
@Getter
@Setter
public class FrequencyExecutionTimePointQueryModel {
    /**
     * 频次编码。
     */
    private String rateCode;
    /**
     * 计划执行时间点。
     */
    private LocalDateTime executionAt;
}
