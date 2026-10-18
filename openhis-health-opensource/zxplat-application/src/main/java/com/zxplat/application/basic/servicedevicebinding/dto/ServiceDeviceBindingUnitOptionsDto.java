/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.servicedevicebinding.dto;

import com.zxplat.framework.core.application.dto.OptionDto;
import lombok.Data;

import java.util.List;

/**
 * 按绑定目标（诊疗或耗材）解析出的默认单位与可选单位下拉，用于保存绑定时单位字段联动。
 */
@Data
public class ServiceDeviceBindingUnitOptionsDto {

    /** 推荐默认选中的单位码（诊疗取销售单位；耗材取多单位字段中的优先非空项）。 */
    private String unitCode;

    /** 可选单位列表（来自术语解析或码值回退）。 */
    private List<OptionDto> unitOptions;
}
