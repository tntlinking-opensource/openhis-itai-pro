/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.executelocation.dto;

import com.openhis.whale.core.application.dto.OptionDto;
import lombok.Data;

import java.util.List;

/**
 * 执行地点配置页初始化数据：执行科室树、诊疗大类下拉；执行地点下拉由页面按科室再加载（初始可为空列表）。
 */
@Data
public class ExecuteLocationInitDto {

    /** 执行科室（组织机构）树形下拉，value 为机构 id 字符串。 */
    private List<OptionDto> executionOrgOptions;
    /** 执行地点候选；当前入口占位为空列表，由业务二次查询填充。 */
    private List<OptionDto> executionLocationOptions;
    /** 诊疗大类（活动类别）选项，来自术语集 {@code activity_category_code}。 */
    private List<OptionDto> activityCategoryOptions;
}
