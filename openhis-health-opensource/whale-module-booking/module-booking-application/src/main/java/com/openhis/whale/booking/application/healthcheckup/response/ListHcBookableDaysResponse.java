/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.response;

import com.openhis.whale.booking.application.healthcheckup.dto.HcBookableDayDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 可约日期列表响应。
 */
@Getter
@Setter
public class ListHcBookableDaysResponse {

    /** 可约日期列表。 */
    private List<HcBookableDayDto> days;
}
