/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 团检到检确认请求（GRP-05）。
 */
@Getter
@Setter
public class ConfirmGroupArrivalRequest {

    /** 名册主键。 */
    private Long rosterId;
}
