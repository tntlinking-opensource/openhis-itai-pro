/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.slot.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 设置号源超售标记请求（3.4）。
 */
@Getter
@Setter
public class SetOverbookedRequest {

    /** 号源主键。 */
    @NotNull
    private Long slotId;

    /** 是否允许超售。 */
    @NotNull
    private Boolean overbooked;
}
