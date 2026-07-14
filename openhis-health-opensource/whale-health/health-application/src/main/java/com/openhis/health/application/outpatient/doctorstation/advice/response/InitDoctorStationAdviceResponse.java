/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 医生站医嘱开立页初始化响应：用药频次、给药途径（用法）与费用性质（合同）下拉；中药用法见 {@code doctorStationTcm/initDoctorStationTcm}。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-医嘱开立-初始化响应")
public class InitDoctorStationAdviceResponse {

    @Schema(description = "用药频次下拉")
    /** 给药途径（用法）下拉。 */
    private List<OptionDto> medicationRateOptions;

    @Schema(description = "给药途径（用法）下拉")
    private List<OptionDto> medicationRouteOptions;

    /**
     * 费用性质（合同）下拉：{@code value} 为合同主键字符串（落库 {@code contract_id}），{@code label} 为名称。
     */
    @Schema(description = "费用性质（合同）下拉")
    private List<OptionDto> feeNatureOptions;

    @Schema(description = "身体部位下拉")
    /** 样本定义下拉。 */
    private List<OptionDto> bodyStructureOptions;

    @Schema(description = "样本定义下拉")
    private List<OptionDto> specimenDefinitionOptions;
}
