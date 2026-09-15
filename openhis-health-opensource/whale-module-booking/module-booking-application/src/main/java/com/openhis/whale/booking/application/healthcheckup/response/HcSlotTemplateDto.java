/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 体检号源模板响应。
 */
@Getter
@Setter
public class HcSlotTemplateDto {

    /** 主键。 */
    private Long id;

    /** 机构主键。 */
    private Long organizationId;

    /** 时段间隔分钟。 */
    private Integer slotMinutes;

    /** 上午开始时刻。 */
    private LocalTime amStart;

    /** 上午结束时刻。 */
    private LocalTime amEnd;

    /** 下午开始时刻。 */
    private LocalTime pmStart;

    /** 下午结束时刻。 */
    private LocalTime pmEnd;

    /** 各合同类型默认容量。 */
    private Map<String, Integer> capacityByType;

    /** 模板休息日 ISO 1-7 集合。 */
    private Set<Integer> restWeekdays;

    /** 校区编码列表。 */
    private List<String> campusCodes;
}
