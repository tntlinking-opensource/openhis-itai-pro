/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.registration.request;

import lombok.Data;

/**
 * 读取收费态请求（REG-08）。
 */
@Data
public class GetChargeStatusRequest {

    /** 就诊主键。 */
    private Long encounterId;
}
