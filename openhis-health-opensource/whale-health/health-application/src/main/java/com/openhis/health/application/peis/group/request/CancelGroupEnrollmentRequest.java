/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 撤销团检免收费建单请求。
 */
@Getter
@Setter
public class CancelGroupEnrollmentRequest {

    /** 名册主键。 */
    private Long rosterId;
}
