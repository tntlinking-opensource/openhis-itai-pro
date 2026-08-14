/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.temperaturesheet.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-TMP-05：删除一条体温单主表请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "删除体温单请求", requiredProperties = {"vitalSignId", "encounterId", "patientId"})
public class DeleteNurseStationTemperatureSheetRequest {

    /** 主表 id。 */
    @NotNull(message = "记录标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "主表 id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long vitalSignId;

    /** 就诊标识。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 患者标识。 */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;
}
