/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.response;

import com.openhis.whale.booking.application.healthcheckup.dto.HcBookableSlotDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 可约时段列表响应。
 */
@Getter
@Setter
public class ListHcBookableSlotsResponse {

    /** 可约时段列表。 */
    private List<HcBookableSlotDto> slots;
}
