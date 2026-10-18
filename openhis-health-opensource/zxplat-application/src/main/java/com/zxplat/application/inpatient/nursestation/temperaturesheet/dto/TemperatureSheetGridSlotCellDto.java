/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.temperaturesheet.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 体温单网格单槽单元（日×6 槽宽表中的一个槽）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体温单网格单槽单元")
public class TemperatureSheetGridSlotCellDto {

    /** 时点槽 code。 */
    @Schema(description = "时点槽 code")
    private String timeSlot;

    /** 实际测量时分（HH:mm），供前端就近描点；无记录时为空。 */
    @Schema(description = "实际测量时分 HH:mm")
    private String recordTime;

    /** 主表 id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "主表 id")
    private Long vitalSignId;

    /** 明细项列表。 */
    @Schema(description = "明细项列表")
    private java.util.List<TemperatureSheetDetailItemDto> detailItems;
}
