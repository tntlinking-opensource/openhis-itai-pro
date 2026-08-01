/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 团检到检确认响应。
 */
@Getter
@Setter
public class ConfirmGroupArrivalResponse {

    private Long rosterId;
    private String arrivalStatusCode;
}
