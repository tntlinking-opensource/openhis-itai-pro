/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.calendarexception.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 按主键加载/删除日历例外。
 */
@Getter
@Setter
public class CalendarExceptionIdRequest {

    /** 日历例外主键。 */
    @NotNull
    private Long id;
}
