/*
 * Copyright © 2026 OpenHIS Contributors
 */
package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.response;

import com.openhis.whale.core.application.dto.OptionDto;
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

    /** 中药用法下拉（tcm_method_code）。 */
    @Schema(description = "中药用法下拉")
    private List<OptionDto> tcmMethodCodeOptions = new ArrayList<>();

    /** 用药频次下拉。 */
    @Schema(description = "用药频次下拉")
    private List<OptionDto> medicationRateOptions = new ArrayList<>();

    /** 长临筛选默认值。 */
    @Schema(description = "长临筛选默认值")
    private String therapyTypeFilterDefault;
}
