/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.department.dto;

import com.zxplat.framework.core.application.dto.OptionDto;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 科室页面初始化数据。
 *
 * <p>用于一次性下发页面首屏所需的下拉选项，减少前端重复请求。</p>
 */
@Data
@Accessors(chain = true)
public class DepartmentInitDto {

    /** 科室类别下拉选项。 */
    private List<OptionDto> departmentClassOptions;
    /** 科室类型下拉选项。 */
    private List<OptionDto> departmentTypeOptions;
    /** 上级组织树下拉选项。 */
    private List<OptionDto> departmentOptions;
    /** 默认医师下拉选项。 */
    private List<OptionDto> practitionerOptions;
}
