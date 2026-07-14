/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 团检批量预约请求（9.6）。
 */
@Getter
@Setter
public class BatchCreateHealthCheckupBookingRequest {

    /** 团检批次主键。 */
    @NotNull
    private Long batchId;

    /** 名单主键列表（为空则预约批次内全部待约名单）。 */
    private List<Long> rosterIds = new ArrayList<>();
}
