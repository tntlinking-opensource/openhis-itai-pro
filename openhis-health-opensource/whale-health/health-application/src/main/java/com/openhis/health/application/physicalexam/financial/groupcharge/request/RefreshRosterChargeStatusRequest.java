/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.groupcharge.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 刷新名册收费态请求。
 */
@Getter
@Setter
public class RefreshRosterChargeStatusRequest {

    /** 名册主键。 */
    private Long rosterId;
}
