/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.schedule.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 按参与者与日期检索读模型。
 */
@Getter
@Setter
public class ScheduleActorDateModel {

    /** 排班主键。 */
    private Long scheduleId;

    /** 参与者类型。 */
    private String actorType;

    /** 参与者主键。 */
    private Long actorId;
}
