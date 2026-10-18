/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.schedule.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 日程参与者读模型。
 */
@Getter
@Setter
public class ScheduleActorModel {

    /** 参与者类型。 */
    private Integer actorType;

    /** 参与者主键。 */
    private Long actorId;

    /** sort号。 */
    private Integer sortNo;
}
