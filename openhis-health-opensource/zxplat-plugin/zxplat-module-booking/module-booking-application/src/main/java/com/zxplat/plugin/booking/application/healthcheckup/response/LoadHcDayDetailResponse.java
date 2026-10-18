/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.response;

import com.zxplat.plugin.booking.application.healthcheckup.dto.HcDayPlanDto;
import com.zxplat.plugin.booking.application.healthcheckup.dto.HcDaySlotDto;
import com.zxplat.plugin.booking.application.healthcheckup.dto.HcDayTypeDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 日明细响应。
 */
@Getter
@Setter
public class LoadHcDayDetailResponse {

    /** 日计划。 */
    private HcDayPlanDto plan;

    /** 开放合同类型列表。 */
    private List<HcDayTypeDto> dayTypes;

    /** 时段配额列表。 */
    private List<HcDaySlotDto> slots;
}
