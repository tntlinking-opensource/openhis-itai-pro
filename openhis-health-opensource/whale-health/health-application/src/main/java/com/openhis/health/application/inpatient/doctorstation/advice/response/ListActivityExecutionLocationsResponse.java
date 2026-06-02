/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inpatient.doctorstation.advice.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * IF-IP-DOR-19：诊疗可执行科室规则列表（执行科室/地点展示名由应用层批量解析回填）。
 */
@Data
@Schema(description = "医生站-诊疗可执行科室规则列表")
public class ListActivityExecutionLocationsResponse {

    /** 匹配诊疗定义的可执行科室规则行，含日内时段与优先级。 */
    @Schema(description = "规则行")
    private List<ActivityExecutionLocationRuleRowDto> rules;
}
