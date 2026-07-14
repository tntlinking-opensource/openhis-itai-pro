/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 门诊医生站中医 Tab 初始化响应：下发中药用法与用药频次术语。 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊医生站中医 Tab 初始化响应")
public class InitDoctorStationTcmResponse {

    /** 中药用法下拉。 */
    @Schema(description = "中药用法下拉")
    private List<OptionDto> tcmMethodCodeOptions;

    /** 用药频次下拉。 */
    @Schema(description = "用药频次下拉")
    private List<OptionDto> medicationRateOptions;

    /** 单味特殊煎法下拉。 */
    @Schema(description = "单味特殊煎法下拉")
    private List<OptionDto> dosageInstructionOptions;
}
