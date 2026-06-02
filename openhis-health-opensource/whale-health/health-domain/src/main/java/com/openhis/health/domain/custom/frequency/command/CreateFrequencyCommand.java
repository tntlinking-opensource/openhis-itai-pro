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

package com.openhis.health.domain.custom.frequency.command;

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
     * 周期类型枚举值。
     */
    private Integer cycleType;
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
     * 允许跨日选项枚举值（可空）。
     */
    private Integer allowCrossDay;
    /**
     * 排序号（可空）。
     */
    private Integer sortNo;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
