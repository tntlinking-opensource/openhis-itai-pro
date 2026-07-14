/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 体温单窗口天数选项（IF-IP-TMP-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体温单窗口选项")
public class TemperatureSheetWindowOptionDto {

    /** 窗口 code。 */
    @Schema(description = "窗口 code")
    private String code;

    /** 展示文案。 */
    @Schema(description = "展示文案")
    private String displayText;

    /** 是否默认。 */
    @Schema(description = "是否默认")
    private Boolean defaultSelected;
}
