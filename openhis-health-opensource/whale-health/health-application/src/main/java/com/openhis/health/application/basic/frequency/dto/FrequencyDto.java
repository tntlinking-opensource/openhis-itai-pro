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

package com.openhis.health.application.basic.frequency.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 用药频次对外展示 DTO：列表与详情共用，字段与领域实体及分页行模型对齐。
 */
@Data
public class FrequencyDto {

    /** 技术主键（JSON 序列化为字符串，避免前端 Long 精度丢失）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 频次业务编码。 */
    private String rateCode;

    /** 频次名称。 */
    private String name;

    /** 周期类型码。 */
    private String cycleType;

    /** 日计数。 */
    private Integer dayCount;

    /** 日间隔。 */
    private Integer dayInterval;

    /** 日内执行时点。 */
    private String dayTimes;

    /** 是否按周循环（1/0）。 */
    private Integer weekCycleFlag;

    /** 总执行次数上限。 */
    private Integer totalExecutionCount;

    /** 执行周期数值。 */
    private Integer executionPeriod;

    /** 执行周期单位。 */
    private String executionPeriodUnit;

    /** 适用场景。 */
    private String applicableScenes;

    /** 跨日选项 code：same-day / cross-day / by-shift。 */
    private String allowCrossDay;

    /** 启用标志：1 启用，0 停用。 */
    private Integer activeFlag;

    /** 排序号。 */
    private Integer sortNo;
}
