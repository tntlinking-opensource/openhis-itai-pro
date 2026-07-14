/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.outpatient.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 日程参与者入参。
 */
@Getter
@Setter
public class ScheduleActorRequest {

    /** 参与者类型（对齐 {@code ScheduleActorKind.sortOrder}）。 */
    @NotNull
    private Integer actorType;

    /** 参与者主键。 */
    @NotNull
    private Long actorId;

    /** 排序号（可选）。 */
    private Integer sortNo;
}
