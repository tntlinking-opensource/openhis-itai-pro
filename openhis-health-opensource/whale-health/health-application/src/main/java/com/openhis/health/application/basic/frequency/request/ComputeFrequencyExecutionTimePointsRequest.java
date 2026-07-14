/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.frequency.request;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 计算频次在给定时间窗内的计划执行时点（委托 {@code FrequencyQueryService#computeExecutionTimePoints}）。
 */
@Data
public class ComputeFrequencyExecutionTimePointsRequest {

    /** 频次编码（rateCode）。 */
    private String rateCode;

    /** 展开窗口起点（含边界语义由领域读侧定义）。 */
    private LocalDateTime startTime;

    /** 展开窗口终点。 */
    private LocalDateTime endTime;
}
