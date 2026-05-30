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

package com.openhis.health.application.basic.frequency.request;

import lombok.Data;

/**
 * 新增用药频次请求体：与领域 {@link com.openhis.health.domain.custom.frequency.command.CreateFrequencyCommand} 字段对齐。
 */
@Data
public class CreateFrequencyRequest {

    /** 频次业务编码（对外唯一标识，创建后一般不可改）。 */
    private String rateCode;

    /** 频次名称（展示与检索）。 */
    private String name;

    /** 按天类周期时的「每隔几天」间隔。 */
    private Integer dayInterval;

    /** 一天内执行时点或次数描述（与 cycleType 组合解释，如多时点 JSON/文本）。 */
    private String dayTimes;

    /** 是否按周循环：1 是，0 否（与前端约定整型）。 */
    private Integer weekCycleFlag;

    /** 按周类周期时的周期间隔（周数）。 */
    private Integer weekInterval;

    /** 限定总执行次数上限（与医嘱执行计划相关）。 */
    private Integer totalExecutionCount;

    /** 执行周期长度数值部分。 */
    private Integer executionPeriod;

    /** 执行周期单位（与 executionPeriod 成对，如天/小时等域内约定码）。 */
    private String executionPeriodUnit;

    /** 第三方系统对照码（对接外院/医保等）。 */
    private String thirdCode;

    /** 备注说明。 */
    private String memo;

    /** 启用标志：1 启用，0 停用（与初始化下拉一致）。 */
    private Integer activeFlag;

    /** 列表/下拉排序号，越小越靠前。 */
    private Integer sortNo;

    /** 周期类型整数码：按天/按周/按月/按小时间隔/PRN 等，与初始化 cycleTypeOptions 对齐。 */
    private Integer cycleType;

    /** 适用场景编码串（多选拼接或单码，与领域存储格式一致）。 */
    private String applicableScenes;

    /** 是否允许跨天：0 否，1 是。 */
    private Integer allowCrossDay;
}
