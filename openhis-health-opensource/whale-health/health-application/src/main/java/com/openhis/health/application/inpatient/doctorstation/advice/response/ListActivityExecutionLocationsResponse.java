/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * IF-IP-DOR-19：诊疗可执行科室规则列表（执行科室/地点展示名由应用层批量解析回填）。
 */
@Data
/** 医生站-诊疗可执行科室规则列表。 */
@Schema(description = "医生站-诊疗可执行科室规则列表")
public class ListActivityExecutionLocationsResponse {

    @Schema(description = "规则行")
    private List<ActivityExecutionLocationRuleRowDto> rules;
}
