/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 录入抽屉单槽回显。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体温单录入槽回显")
public class TemperatureSheetFillSlotDto {

    /** 时点槽 code。 */
    @Schema(description = "时点槽 code")
    private String timeSlot;

    /** 主表 id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "主表 id")
    private Long vitalSignId;

    /** 明细项列表。 */
    @Schema(description = "明细项列表")
    private java.util.List<TemperatureSheetDetailItemDto> detailItems;
}
