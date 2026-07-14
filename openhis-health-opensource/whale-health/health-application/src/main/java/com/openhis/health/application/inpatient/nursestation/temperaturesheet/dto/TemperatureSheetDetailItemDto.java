/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 体温单明细项（IF-IP-TMP-02/03 统一读写结构）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体温单明细项")
public class TemperatureSheetDetailItemDto {

    /** 术语体系 code（如 {@code vital_signs_code}、{@code special_event_code}）。 */
    @Schema(description = "术语体系 code")
    private String termCode;

    /** 字典项 code。 */
    @Schema(description = "字典项 code")
    private String itemCode;

    /** 字典项值。 */
    @Schema(description = "字典项值")
    private String itemValue;

    /** 明细备注（可空）。 */
    @Schema(description = "明细备注")
    private String remark;
}
