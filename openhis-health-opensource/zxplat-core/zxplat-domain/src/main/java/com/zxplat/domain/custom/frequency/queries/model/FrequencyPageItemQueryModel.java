/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.frequency.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 频次分页项查询模型。
 */
@Getter
@Setter
public class FrequencyPageItemQueryModel {
    /** 频次主键 {@code cus_frequency.id}。 */
    private Long id;
    /** 频次编码（业务唯一键）。 */
    private String rateCode;
    /** 频次展示名称。 */
    private String name;
    /** 周期类型（日/周等字典码）。 */
    private String cycleType;
    /** 周期内天数。 */
    private Integer dayCount;
    /** 周期间隔（每隔 N 天/周）。 */
    private Integer dayInterval;
    /** 周期内执行时点（如 8-12-18）。 */
    private String dayTimes;
    /** 是否按周循环。 */
    private Boolean weekCycleFlag;
    /** 总执行次数（疗程总次数，可空）。 */
    private Integer totalExecutionCount;
    /** 执行周期长度。 */
    private Integer executionPeriod;
    /** 执行周期单位编码。 */
    private String executionPeriodUnit;
    /** 适用场景（门诊/住院等，多值序列化）。 */
    private String applicableScenes;
    /** 是否允许跨日执行。 */
    private String allowCrossDay;
    /** 是否启用。 */
    private Boolean activeFlag;
    /** 排序号。 */
    private Integer sortNo;
}
