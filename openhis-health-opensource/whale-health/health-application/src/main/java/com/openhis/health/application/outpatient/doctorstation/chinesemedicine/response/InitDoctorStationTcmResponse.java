/*
 * Copyright © 2026 OpenHIS Contributors
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

    /** 中药用法下拉（术语 {@code tcm_method_code}，不含西医 {@code method_code}）。 */
    @Schema(description = "中药用法下拉")
    private List<OptionDto> tcmMethodCodeOptions;

    /** 用药频次下拉（与医嘱 Tab {@code medicationRateOptions} 同源）。 */
    @Schema(description = "用药频次下拉")
    private List<OptionDto> medicationRateOptions;
}
