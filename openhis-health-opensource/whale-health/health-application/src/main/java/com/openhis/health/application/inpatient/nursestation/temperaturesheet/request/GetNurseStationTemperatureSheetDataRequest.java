/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.temperaturesheet.request;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * IF-IP-TMP-02：加载体温单曲线与网格请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "加载体温单数据请求", requiredProperties = {
    "encounterId", "patientId", "windowRange"
})
public class GetNurseStationTemperatureSheetDataRequest {

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

    /** 窗口范围：3d/7d/14d。 */
    @NotBlank(message = "窗口范围不能为空")
    @Schema(description = "窗口范围：3d/7d/14d", requiredMode = Schema.RequiredMode.REQUIRED)
    private String windowRange;

    /** 页码（0-based）。 */
    @Schema(description = "页码（0-based）")
    private Integer pageIndex;

    /** 录入抽屉回显日。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    @Schema(description = "录入抽屉回显日")
    private LocalDate focusRecordDate;
}
