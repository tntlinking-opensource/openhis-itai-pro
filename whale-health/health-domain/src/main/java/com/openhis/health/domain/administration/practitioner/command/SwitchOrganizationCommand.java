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

package com.openhis.health.domain.administration.practitioner.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 切换医疗卫生人员所属机构命令（批量）。
 * <p>
 * 将人员的主执业或人事归属从原机构调整至目标机构（如调动）。
 */
@Getter
@Setter
public class SwitchOrganizationCommand extends BatchCommand<SwitchOrganizationCommand.SwitchOrganizationItemCommand> {
    /** 执行本次批量机构切换的操作人标识。 */
    private Long operatorId;

    /** 单条切换项：人员与目标机构。 */
    @Getter
    @Setter
    public static class SwitchOrganizationItemCommand {
        /** 待调整机构的医疗卫生人员主键。 */
        private Long practitionerId;
        /** 目标组织机构主键。 */
        private Long targetOrgId;
    }
}
