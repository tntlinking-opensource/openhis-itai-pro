/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.chinesemedicine.response;

import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * IF-IP-TCM-29：住院中医 Tab 初始化响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院中医 Tab 初始化响应")
public class InitInpatientDoctorStationTcmResponse {

    /** 中药用法下拉。 */
    @Schema(description = "中药用法下拉")
    private List<OptionDto> tcmMethodCodeOptions = new ArrayList<>();

    /** 用药频次下拉。 */
    @Schema(description = "用药频次下拉")
    private List<OptionDto> medicationRateOptions = new ArrayList<>();

    /** 单味特殊煎法下拉。 */
    @Schema(description = "单味特殊煎法下拉")
    private List<OptionDto> dosageInstructionOptions = new ArrayList<>();

    /** 长临筛选默认值。 */
    @Schema(description = "长临筛选默认值")
    private String therapyTypeFilterDefault;
}
