/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.outpatient.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 日程参与者出站 DTO。
 */
@Getter
@Setter
public class ScheduleActorDto {

    /** 参与者类型。 */
    private Integer actorType;

    /** 参与者主键。 */
    private Long actorId;

    /** 排序号。 */
    private Integer sortNo;
}
