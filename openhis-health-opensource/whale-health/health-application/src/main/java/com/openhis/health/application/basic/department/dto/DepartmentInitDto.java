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

package com.openhis.health.application.basic.department.dto;

import com.openhis.whale.core.application.dto.OptionDto;
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
