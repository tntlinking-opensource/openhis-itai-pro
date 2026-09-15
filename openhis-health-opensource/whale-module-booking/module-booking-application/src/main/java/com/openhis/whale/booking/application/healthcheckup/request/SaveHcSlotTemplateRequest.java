/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;
import java.util.Map;

/**
 * 保存体检号源模板请求。
 */
@Getter
@Setter
public class SaveHcSlotTemplateRequest {

    /** 机构主键。 */
    @NotNull
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
    private java.util.Set<Integer> restWeekdays;

    /** 生成时展开的校区编码列表。 */
    private java.util.List<String> campusCodes;
}
