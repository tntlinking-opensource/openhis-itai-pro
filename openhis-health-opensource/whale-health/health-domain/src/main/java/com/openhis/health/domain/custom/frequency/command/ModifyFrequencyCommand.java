/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.frequency.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 修改频次命令。
 */
@Getter
@Setter
public class ModifyFrequencyCommand {
    /**
     * 频次标识。
     */
    private Long id;
    /**
     * 频次名称（可空；非空则覆盖）。
     */
    private String name;
    /**
     * 周期类型 code（可空；非空则覆盖）。
     */
    private String cycleType;
    /**
     * 日计数（可空；非空则覆盖）。
     */
    private Integer dayCount;
    /**
     * 日间隔（可空；非空则覆盖）。
     */
    private Integer dayInterval;
    /**
     * 日执行时点串（可空；非空则覆盖）。
     */
    private String dayTimes;
    /**
     * 周周期标记（可空；非空则覆盖）。
     */
    private Boolean weekCycleFlag;
    /**
     * 总执行次数上限（可空；非空则覆盖）。
     */
    private Integer totalExecutionCount;
    /**
     * 执行周期数值（可空；非空则覆盖）。
     */
    private Integer executionPeriod;
    /**
     * 执行周期单位（可空；非空则覆盖）。
     */
    private String executionPeriodUnit;
    /**
     * 适用场景（可空；非空则覆盖）。
     */
    private String applicableScenes;
    /**
     * 允许跨日选项 code（可空；非空则覆盖）。
     */
    private String allowCrossDay;
    /**
     * 排序号（可空；非空则覆盖）。
     */
    private Integer sortNo;
    /**
     * 并发戳（可空；由上层在需要时做乐观锁校验）。
     */
    private String concurrencyStamp;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
