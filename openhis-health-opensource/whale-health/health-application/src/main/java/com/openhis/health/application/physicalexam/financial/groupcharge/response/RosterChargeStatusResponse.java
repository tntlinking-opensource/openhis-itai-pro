/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.groupcharge.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 名册收费态响应。
 */
@Getter
@Setter
public class RosterChargeStatusResponse {

    private Long rosterId;
    private String payStatusCode;
    private Long encounterId;
    private boolean paid;
}
