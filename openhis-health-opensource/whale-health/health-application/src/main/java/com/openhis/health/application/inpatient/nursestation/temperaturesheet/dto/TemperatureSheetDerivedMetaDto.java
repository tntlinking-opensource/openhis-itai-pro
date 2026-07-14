/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 体温单读侧派生元数据（住院天数、周次、术后天数）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体温单派生元数据")
public class TemperatureSheetDerivedMetaDto {

    /** 日期 → 住院天数。 */
    @Schema(description = "日期 → 住院天数")
    private Map<String, Integer> hospitalizationDaysByDate = new LinkedHashMap<>();

    /** 日期 → 周次。 */
    @Schema(description = "日期 → 周次")
    private Map<String, Integer> weekNoByDate = new LinkedHashMap<>();

    /** 日期 → 术后天数。 */
    @Schema(description = "日期 → 术后天数")
    private Map<String, Integer> postoperativeDaysByDate = new LinkedHashMap<>();
}
