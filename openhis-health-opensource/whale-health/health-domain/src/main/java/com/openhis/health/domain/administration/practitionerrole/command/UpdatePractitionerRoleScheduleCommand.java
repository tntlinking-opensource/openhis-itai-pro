/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
