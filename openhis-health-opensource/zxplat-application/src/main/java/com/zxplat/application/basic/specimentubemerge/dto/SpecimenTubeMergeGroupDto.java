/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.specimentubemerge.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备下单个合管组绑定详情（展示名「组N」由 {@link #groupNo} 推导，不落库）。
 */
@Data
public class SpecimenTubeMergeGroupDto {

    /** 设备内合管组序号（从 1 起）。 */
    private Integer groupNo;

    /** 展示名（如「组1」）；只读回填。 */
    private String groupName;

    /** 是否绑定采血费 activity。 */
    private Boolean bindBloodCollectionFee;

    /** 检验项目 activity 主键（不含采血费）。 */
    private List<String> activityIds = new ArrayList<>();

    /** 耗材绑定行。 */
    private List<SpecimenTubeMergeConsumableLineDto> consumableLines = new ArrayList<>();
}
