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
 * 退休/注销医疗卫生人员命令（批量）。
 * <p>
 * 人员永久退出执业岗位（退休、离职注销等），需记录原因；与临时停用区分。
 */
@Getter
@Setter
public class RetirePractitionerCommand extends BatchCommand<RetirePractitionerCommand.RetirePractitionerItemCommand> {
    /** 执行本次批量退休/注销的操作人标识。 */
    private Long operatorId;

    /** 单条退休项：目标人员及原因。 */
    @Getter
    @Setter
    public static class RetirePractitionerItemCommand {
        /** 待退休/注销的医疗卫生人员主键。 */
        private Long practitionerId;
        /** 退休或注销原因说明。 */
        private String reason;
    }
}
