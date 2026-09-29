/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.executelocation.dto;

import com.openhis.whale.core.application.dto.OptionDto;
import lombok.Data;

import java.util.List;

/**
 * 执行地点配置页初始化数据：开立科室树（门诊/住院）、执行科室下拉（护士站/医技）、诊疗大类。
 */
@Data
public class ExecuteLocationInitDto {

    /** 开立科室树：仅门诊、住院科室，医院根仅作上级挂载。 */
    private List<OptionDto> organizationOptions;
    /** 执行科室下拉：仅护士站、医技。医生站无规则时不读本列表，改回落开立科室。 */
    private List<OptionDto> executionOrgOptions;
    /** 执行地点候选；当前入口占位为空列表，由业务二次查询填充。 */
    private List<OptionDto> executionLocationOptions;
    /** 诊疗大类（活动类别）选项，来自术语集 {@code activity_category_code}。 */
    private List<OptionDto> activityCategoryOptions;
}
