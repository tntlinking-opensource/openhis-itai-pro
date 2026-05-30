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

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
/**
 * 更新岗位排班命令。
 *
 * <p>对应 DM-ADM-006 §6.1「更新排班信息（updateSchedule）」与 SDD §5.1.7。</p>
 */
public class UpdatePractitionerRoleScheduleCommand extends BatchCommand<UpdatePractitionerRoleScheduleCommand.UpdatePractitionerRoleScheduleItemCommand> {
    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    /**
     * 更新岗位排班命令项。
     */
    public static class UpdatePractitionerRoleScheduleItemCommand {
        /** 岗位角色标识。 */
        private Long assignmentId;
        /** 排班集合。 */
        private List<AvailabilityScheduleCommand> scheduleItems;
    }
}
