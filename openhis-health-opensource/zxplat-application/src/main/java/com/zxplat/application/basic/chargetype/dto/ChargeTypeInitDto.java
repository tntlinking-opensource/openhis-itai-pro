/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.chargetype.dto;

import com.zxplat.application.basic.chargetype.service.ChargeTypeAppService;
import com.zxplat.framework.core.application.dto.OptionDto;
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
