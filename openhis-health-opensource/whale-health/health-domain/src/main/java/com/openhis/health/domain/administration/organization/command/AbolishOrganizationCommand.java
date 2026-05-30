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
 * 废弃机构命令：机构永久下线并记录废止原因，通常不可再用于新开业务。
 */
@Getter
@Setter
public class AbolishOrganizationCommand extends BatchCommand<AbolishOrganizationCommand.AbolishOrganizationItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

    /** 单条废止项。 */
    @Getter
    @Setter
    public static class AbolishOrganizationItemCommand {
        /** 机构主键。 */
        private Long organizationId;
        /** 废止原因。 */
        private String abolishReason;
    }
}
