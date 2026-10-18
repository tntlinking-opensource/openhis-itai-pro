/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.frequency.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 频次配置页初始化查询模型。
 */
@Getter
@Setter
public class FrequencyConfigInitQueryModel {
    /**
     * 周期类型候选值列表（用于前端初始化）。
     */
    private List<String> cycleTypes;
    /**
     * 周期单位候选值列表（用于前端初始化）。
     */
    private List<String> periodUnits;
    /**
     * 适用场景候选值列表（用于前端初始化）。
     */
    private List<String> applicableScenes;
    /**
     * 跨日选项候选值列表（用于前端初始化）。
     */
    private List<String> allowCrossDayOptions;
}
