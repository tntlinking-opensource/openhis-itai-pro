/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.registration.request;

import lombok.Data;

/**
 * 加载体检指引单打印 slip 请求。
 */
@Data
public class LoadGuidePrintSlipRequest {

    /** 就诊标识。 */
    private Long encounterId;
}
