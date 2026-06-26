/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.module.common.dto.nursestation.TemperatureSheetDetailItemDto;
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

    /** 时点槽 code（02/06/10/14/18/22）。 */
    @Schema(description = "时点槽 code")
    private String timeSlot;

    /** 该槽对应主表 id；无数据时为 null。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "主表 id")
    private Long vitalSignId;

    /** 明细项。 */
    @Schema(description = "明细项列表")
    private java.util.List<TemperatureSheetDetailItemDto> detailItems;
}
