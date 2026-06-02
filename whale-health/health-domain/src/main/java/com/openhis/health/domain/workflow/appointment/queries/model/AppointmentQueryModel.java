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

package com.openhis.health.domain.workflow.appointment.queries.model;

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

