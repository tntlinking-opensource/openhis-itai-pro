/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.history.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 取消未收费体检请求。
 */
@Getter
@Setter
public class CancelPhysicalExamRequest {

    /** 就诊主键。 */
    private Long encounterId;
}
