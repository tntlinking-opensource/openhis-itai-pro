/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.frequency.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 新增频次命令。
 */
@Getter
@Setter
public class CreateFrequencyCommand {
    /**
     * 频次编码（可空；若由系统生成则忽略）。
     */
    private String rateCode;
    /**
     * 频次名称。
     */
    private String name;
    /**
     * 周期类型 code（{@link com.zxplat.domain.share.enums.FrequencyCycleType}）。
     */
    private String cycleType;
    /**
     * 日计数（可空）。
     */
    private Integer dayCount;
    /**
     * 日间隔（可空）。
     */
    private Integer dayInterval;
    /**
     * 日执行时点串（逗号分隔，形如 "08:00,12:00"；可空）。
     */
    private String dayTimes;
    /**
     * 周周期标记（可空）。
     */
    private Boolean weekCycleFlag;
    /**
     * 总执行次数上限（可空）。
     */
    private Integer totalExecutionCount;
    /**
     * 执行周期数值（可空）。
     */
    private Integer executionPeriod;
    /**
     * 执行周期单位（min/h/d/wk/mo；可空）。
     */
    private String executionPeriodUnit;
    /**
     * 适用场景（逗号分隔；可空）。
     */
    private String applicableScenes;
    /**
     * 允许跨日选项 code（{@link com.zxplat.domain.share.enums.FrequencyAllowCrossDay}；可空）。
     */
    private String allowCrossDay;
    /**
     * 排序号（可空）。
     */
    private Integer sortNo;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
