/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.temperaturesheet.response;

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

    /** 主表 id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "主表 id")
    private Long vitalSignId;

    /** 业务单号。 */
    @Schema(description = "业务单号")
    private String busNo;

    /** 下一时点槽。 */
    @Schema(description = "下一时点槽")
    private String nextTimeSlot;
}
