/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.temperaturesheet.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-TMP-03：保存单槽响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "保存体温单单槽响应")
public class SaveNurseStationTemperatureSheetAtRecordedTimeResponse {

    /** 持久化后主表 id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "主表 id")
    private Long vitalSignId;

    /** 业务单号。 */
    @Schema(description = "业务单号")
    private String busNo;

    /** 前端聚焦的下一时点槽；末槽为 null。 */
    @Schema(description = "下一时点槽")
    private String nextTimeSlot;
}
