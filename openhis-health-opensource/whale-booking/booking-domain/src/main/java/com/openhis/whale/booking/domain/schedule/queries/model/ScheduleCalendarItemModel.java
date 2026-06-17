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

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日历视图日程读模型。
 */
@Getter
@Setter
public class ScheduleCalendarItemModel {

    /** 日程主键。 */
    private Long id;

    /** 机构主键。 */
    private Long organizationId;

    /** 显示名称。 */
    private String name;

    /** 业务状态编码。 */
    private String statusEnum;

    /** 规划窗口起。 */
    private LocalDateTime planningHorizonStart;

    /** 规划窗口止。 */
    private LocalDateTime planningHorizonEnd;

    /** 关联医疗服务主键。 */
    private Long healthcareServiceId;

    /** 参与者列表。 */
    private List<ScheduleActorModel> actors;

    /** 替诊医师主键。 */
    private Long substitutePractitionerId;

    /** 替诊原因。 */
    private String substituteReason;
}
