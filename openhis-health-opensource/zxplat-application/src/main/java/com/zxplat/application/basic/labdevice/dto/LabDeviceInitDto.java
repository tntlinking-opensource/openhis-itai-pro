/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.labdevice.dto;

import com.zxplat.framework.core.application.dto.OptionDto;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 检验设备维护页初始化数据。
 */
@Data
public class LabDeviceInitDto {

    /** 设备分类下拉（术语 adm_device_category_code）。 */
    private List<OptionDto> categoryCodeOptions;

    /** 发布状态下拉。 */
    private List<OptionDto> statusEnumOptions;

    /** 归属科室下拉。 */
    private List<OptionDto> organizationOptions;

    /** 生产厂家下拉（cus_supplier type=MANUFACTURER，label/value 行）。 */
    private List<Map<String, Object>> manufacturerOptions;
}
