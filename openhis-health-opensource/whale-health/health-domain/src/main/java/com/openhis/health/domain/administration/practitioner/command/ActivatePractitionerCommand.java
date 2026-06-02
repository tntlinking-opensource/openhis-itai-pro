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
 * 激活医疗卫生人员命令（批量）。
 * <p>
 * 将处于停用等状态的医师/卫技人员恢复为可执业、可排班状态。
 */
@Getter
@Setter
public class ActivatePractitionerCommand extends BatchCommand<ActivatePractitionerCommand.ActivatePractitionerItemCommand> {
    /** 执行本次批量激活的操作人标识。 */
    private Long operatorId;

    /** 单条激活项：指定待激活的医疗卫生人员。 */
    @Getter
    @Setter
    public static class ActivatePractitionerItemCommand {
        /** 待激活的医疗卫生人员主键。 */
        private Long practitionerId;
    }
}
