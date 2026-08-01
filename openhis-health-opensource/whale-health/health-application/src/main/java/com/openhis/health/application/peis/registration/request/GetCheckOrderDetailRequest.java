/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.registration.request;

import lombok.Data;

/**
 * 体检单详情请求（REG-03）。
 */
@Data
public class GetCheckOrderDetailRequest {

    /** 就诊主键。 */
    private Long encounterId;
}
