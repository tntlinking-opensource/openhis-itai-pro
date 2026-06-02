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
 * 废弃岗位角色命令（逻辑删除）。
 *
 * <p>对应 DM-ADM-006 §6.1「废弃岗位（retireRole）」与 SDD §5.1.5。</p>
 */
public class RetirePractitionerRoleCommand extends BatchCommand<RetirePractitionerRoleCommand.RetirePractitionerRoleItemCommand> {
    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    /**
     * 废弃岗位角色命令项。
     */
    public static class RetirePractitionerRoleItemCommand {
        /** 岗位角色标识。 */
        private Long assignmentId;
        /** 废弃原因。 */
        private String reason;
    }
}
