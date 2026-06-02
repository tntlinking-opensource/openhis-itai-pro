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

package com.openhis.health.domain.administration.practitionerrole.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 岗位可用时间（排班）命令项。
 *
 * <p>对应 DM-ADM-006 §3.3 与 SDD §5.1.1/§5.1.7 中的 {@code scheduleItems}。</p>
 */
@Getter
@Setter
public class AvailabilityScheduleCommand {
    /** 可用时间 JSON。 */
    private String availabilityJson;
}

