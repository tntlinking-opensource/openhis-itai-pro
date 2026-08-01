/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.registration.request;

import lombok.Data;

/**
 * 唤起收费上下文请求（REG-09）。
 */
@Data
public class GetChargeLaunchContextRequest {

    /** 就诊主键。 */
    private Long encounterId;
}
