/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.warehouse.dto;

import com.zxplat.framework.core.application.dto.OptionDto;
import lombok.Data;

import java.util.List;

/**
 * 库房维护页初始化：状态字典、上级科室选项、药库/药房/科室药房形态固定选项。
 */
@Data
public class WarehouseInitDto {
    /** 位置状态下拉（术语枚举选项）。 */
    private List<OptionDto> locationStatusOptions;
    /** 上级科室下拉（科室药房登记用，value 为机构 id 字符串）。 */
    private List<OptionDto> organizationOptions;
    /** 仓库与药房、科室药房形态固定下拉。 */
    private List<OptionDto> formEnumOptions;
}
