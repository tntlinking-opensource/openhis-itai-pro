/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 撤销团检到检请求。
 */
@Getter
@Setter
public class CancelGroupArrivalRequest {

    /** 名册主键。 */
    private Long rosterId;
}
