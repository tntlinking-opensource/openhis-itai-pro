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

package com.openhis.whale.booking.domain.appointment.queries;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * IF-REG-12：挂号台加载有效门诊预约检索条件。
 */
@Getter
@Setter
public class ActiveOutpatientBookingLookupQuery {

    /** 患者主键。 */
    private Long patientId;

    /** 预约单号（busNo）。 */
    private String appointmentBusNo;

    /** 挂号自然日；默认当日。 */
    private LocalDate registrationDate;
}
