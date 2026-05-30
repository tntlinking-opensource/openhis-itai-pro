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

package com.openhis.health.domain.administration.organization.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 停用机构命令：机构业务暂停，需记录停用原因（可恢复，与废止区分）。
 */
@Getter
@Setter
public class RetireOrganizationCommand extends BatchCommand<RetireOrganizationCommand.RetireOrganizationItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

    /** 单条停用项。 */
    @Getter
    @Setter
    public static class RetireOrganizationItemCommand {
        /** 机构主键。 */
        private Long organizationId;
        /** 停用原因。 */
        private String retireReason;
    }
}
