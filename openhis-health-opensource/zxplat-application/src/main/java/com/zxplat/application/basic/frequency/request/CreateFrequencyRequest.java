/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.frequency.request;

import lombok.Data;

/**
 * 新增用药频次请求体：与领域 {@link com.zxplat.domain.custom.frequency.command.CreateFrequencyCommand} 字段对齐。
 */
@Data
public class CreateFrequencyRequest {

    /** 频次业务编码（对外唯一标识，创建后一般不可改）。 */
    private String rateCode;

    /** 频次名称（展示与检索）。 */
    private String name;

    /** 周期类型 code（DM-CUS-008 §4.1，与 init cycleTypeOptions 对齐）。 */
    private String cycleType;

    /** 日计数（如「每 N 日」中的 N）。 */
    private Integer dayCount;

    /** 日间隔（如隔日=2）。 */
    private Integer dayInterval;

    /** 一天内执行时点（逗号分隔 HH:mm，如 08:00,12:00,18:00）。 */
    private String dayTimes;

    /** 是否按周循环：1 是，0 否（与前端约定整型）。 */
    private Integer weekCycleFlag;

    /** 限定总执行次数上限。 */
    private Integer totalExecutionCount;

    /** 执行周期长度数值部分。 */
    private Integer executionPeriod;

    /** 执行周期单位（min/h/d/wk/mo，与 init periodUnitOptions 对齐）。 */
    private String executionPeriodUnit;

    /** 启用标志：1 启用，0 停用。 */
    private Integer activeFlag;

    /** 列表/下拉排序号，越小越靠前。 */
    private Integer sortNo;

    /** 适用场景编码串（多选逗号分隔，与 init sceneOptions 对齐）。 */
    private String applicableScenes;

    /** 跨日选项 code：same-day / cross-day / by-shift。 */
    private String allowCrossDay;
}
