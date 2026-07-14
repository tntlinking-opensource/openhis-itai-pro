/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.temperaturesheet.response;

import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetTimeSlotOptionDto;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetWindowOptionDto;
import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * IF-IP-TMP-01：体温单 Tab 页初始化响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体温单页初始化响应")
public class LoadNurseStationTemperatureSheetInitResponse {

    /** 三测时点槽选项。 */
    @Schema(description = "三测时点槽选项")
    private List<TemperatureSheetTimeSlotOptionDto> timeSlotOptions = new ArrayList<>();

    /** 窗口选项。 */
    @Schema(description = "窗口选项")
    private List<TemperatureSheetWindowOptionDto> windowOptions = new ArrayList<>();

    /** 默认窗口 code。 */
    @Schema(description = "默认窗口 code")
    private String defaultWindowRange;

    /** 特殊事件选项。 */
    @Schema(description = "特殊事件选项")
    private List<OptionDto> specialEventOptions = new ArrayList<>();
}
