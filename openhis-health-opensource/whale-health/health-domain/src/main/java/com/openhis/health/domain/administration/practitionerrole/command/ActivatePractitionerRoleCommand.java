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

@Getter
@Setter
/**
 * 启用岗位角色命令。
 *
 * <p>对应 DM-ADM-006 §6.1「启用岗位（activateRole）」与 SDD §5.1.3。</p>
 */
public class ActivatePractitionerRoleCommand extends BatchCommand<ActivatePractitionerRoleCommand.ActivatePractitionerRoleItemCommand> {
    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    /**
     * 启用岗位角色命令项。
     */
    public static class ActivatePractitionerRoleItemCommand {
        /** 岗位角色标识。 */
        private Long assignmentId;
    }
}
