/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.medtech.response;

import com.zxplat.plugin.booking.application.register.response.BookableSlotDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 医技可约号源列表响应。
 */
@Getter
@Setter
public class ListDiagnosticBookableSlotsResponse {

    /** 可约号源列表。 */
    private List<BookableSlotDto> slots;
}
