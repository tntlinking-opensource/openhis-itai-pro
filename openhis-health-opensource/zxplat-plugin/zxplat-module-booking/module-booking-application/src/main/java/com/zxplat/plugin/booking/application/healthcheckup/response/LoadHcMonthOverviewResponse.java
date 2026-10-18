/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.response;

import com.zxplat.plugin.booking.application.healthcheckup.dto.HcDaySummaryDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 月历总览响应。
 */
@Getter
@Setter
public class LoadHcMonthOverviewResponse {

    /** 日摘要列表。 */
    private List<HcDaySummaryDto> days;
}
