/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.temperaturesheet.request;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetDetailItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * IF-IP-TMP-03：按记录时点保存/覆盖三测单请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "保存体温单单槽请求", requiredProperties = {
    "encounterId", "patientId", "recordDate", "timeSlot"
})
public class SaveNurseStationTemperatureSheetAtRecordedTimeRequest {

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

    /** 业务日期。 */
    @NotNull(message = "记录日期不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    @Schema(description = "业务日期", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate recordDate;

    /** 三测时点槽。 */
    @NotBlank(message = "测量时点不能为空")
    @Schema(description = "三测时点槽", requiredMode = Schema.RequiredMode.REQUIRED)
    private String timeSlot;

    /** 已有主表 id（更新）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "已有主表 id（更新）")
    private Long vitalSignId;

    /** 明细项列表。 */
    @Schema(description = "明细项列表")
    private java.util.List<TemperatureSheetDetailItemDto> detailItems;
}
