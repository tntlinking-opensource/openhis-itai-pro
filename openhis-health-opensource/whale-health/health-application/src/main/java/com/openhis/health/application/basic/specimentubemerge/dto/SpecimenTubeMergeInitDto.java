/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.specimentubemerge.dto;

import com.openhis.whale.core.application.dto.OptionDto;
import lombok.Data;

import java.util.List;

/**
 * 标本合管配置页初始化数据。
 */
@Data
public class SpecimenTubeMergeInitDto {

    /** 设备检索提示文案。 */
    private String deviceSearchHint;

    /** 检验类诊疗目录类别 code（activity 筛选）。 */
    private String labActivityCategoryCode;

    /** 单位下拉。 */
    private List<OptionDto> unitOptions;

    /** 绑定目标表选项。 */
    private List<OptionDto> targetTableOptions;
}
