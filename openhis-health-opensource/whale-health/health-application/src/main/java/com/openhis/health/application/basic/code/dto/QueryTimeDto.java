/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.code.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 时间范围过滤条件（开始/结束时刻 + 可选字段名）。
 */
@Data
public class QueryTimeDto {

    /** 区间起点（含）。 */
    @JsonFormat(pattern = GeneralSharedConstants.DATE_TIME_PATTERN, timezone = "GMT+8")
    private LocalDateTime startTime;

    /** 区间终点（含）。 */
    @JsonFormat(pattern = GeneralSharedConstants.DATE_TIME_PATTERN, timezone = "GMT+8")
    private LocalDateTime endTime;

    /** 对应实体上的时间字段名（由调用方解析）。 */
    private String fieldName;
}
