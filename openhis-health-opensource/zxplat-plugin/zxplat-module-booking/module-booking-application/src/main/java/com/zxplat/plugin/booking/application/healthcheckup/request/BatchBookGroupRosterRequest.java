/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 团检批量预约请求（6.x 旧版 API 别名，对应 BatchCreateHealthCheckupBookingRequest）。
 */
@Getter
@Setter
public class BatchBookGroupRosterRequest {

    /** 团检批次主键。 */
    @NotNull
    private Long batchId;

    /** 名单主键列表。 */
    private List<Long> rosterIds;
}
