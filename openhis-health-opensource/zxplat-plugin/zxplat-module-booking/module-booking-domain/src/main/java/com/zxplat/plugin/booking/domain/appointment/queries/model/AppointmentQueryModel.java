/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.appointment.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 按就诊查询有效项的查询模型。
 */
@Getter
@Setter
public class AppointmentQueryModel {
    /** 主键。 */
    private Long id;
    /** 预约kind。 */
    private String appointmentKind;
    /** display医嘱。 */
    private Integer displayOrder;
    /** 状态view。 */
    private String statusView;
}
