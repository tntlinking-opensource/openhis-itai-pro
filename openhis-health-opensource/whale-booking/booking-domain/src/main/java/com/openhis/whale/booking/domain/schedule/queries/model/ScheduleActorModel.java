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

package com.openhis.whale.booking.domain.schedule.queries.model;

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

