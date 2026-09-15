/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 生成未来日计划响应。
 */
@Getter
@Setter
public class GenerateHcFutureDaysResponse {

    /** 实际生成的日计划数量。 */
    private Integer generatedDayCount;
}
