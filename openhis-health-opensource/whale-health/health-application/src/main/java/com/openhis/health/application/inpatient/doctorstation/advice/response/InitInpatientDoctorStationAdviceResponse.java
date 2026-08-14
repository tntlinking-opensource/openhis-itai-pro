/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-DOR-18：医嘱开立页初始化响应（用药频次、给药途径、输液滴速术语下拉与费用性质下拉）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-医嘱开立-初始化响应")
public class InitInpatientDoctorStationAdviceResponse {

    @Schema(description = "用药频次下拉")
    /** 给药途径（用法）下拉。 */
    private List<OptionDto> medicationRateOptions;

    @Schema(description = "给药途径（用法）下拉")
    private List<OptionDto> medicationRouteOptions;

    /**
     * 输液滴速下拉（术语 {@code speed_code}；输液/注射药开立用）。
     */
    @Schema(description = "输液滴速下拉")
    private List<OptionDto> medicationSpeedOptions;

    /**
     * 费用性质（合同）下拉：{@code value} 为合同主键字符串（落库 {@code contract_id}），{@code label} 为名称。
     */
    @Schema(description = "费用性质（合同）下拉")
    private List<OptionDto> feeNatureOptions;

    /**
     * 身体部位树形下拉（仅 ACTIVE；含 children；节点带 pyStr/wbStr 供前端模糊）。
     */
    @Schema(description = "身体部位树形下拉（含拼音/五笔检索码）")
    private List<OptionDto> bodyStructureOptions;

    /**
     * 样本定义平面下拉（启用态；带 pyStr/wbStr 供前端模糊）。
     */
    @Schema(description = "样本定义下拉（含拼音/五笔检索码）")
    private List<OptionDto> specimenDefinitionOptions;
}
