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

package com.openhis.health.application.basic.chargetype.dto;

import com.openhis.health.application.basic.chargetype.service.ChargeTypeAppService;
import com.openhis.whale.core.application.dto.OptionDto;
import lombok.Data;

import java.util.List;

/**
 * 费用性质（收费类型）表单/列表初始化数据：下拉选项等只读片段。
 *
 * <p>分类选项与 {@link ChargeTypeAppService#init()} 中约定一致，
 * 供前端渲染「医保/自费」等大类筛选或新建默认值。</p>
 */
@Data
public class ChargeTypeInitDto {

    /** 费用性质分类下拉项（value 为顺序占位，code 为业务分类码，label 为中文展示）。 */
    private List<OptionDto> categoryOptions;
}
