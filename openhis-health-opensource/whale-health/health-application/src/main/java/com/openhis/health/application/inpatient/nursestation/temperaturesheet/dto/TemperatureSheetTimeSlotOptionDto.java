/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 三测时点槽选项（IF-IP-TMP-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "三测时点槽选项")
public class TemperatureSheetTimeSlotOptionDto {

    /** 槽 code（02～22）。 */
    @Schema(description = "槽 code")
    private String code;

    /** 展示文案（如 02:00）。 */
    @Schema(description = "展示文案")
    private String displayText;
}
