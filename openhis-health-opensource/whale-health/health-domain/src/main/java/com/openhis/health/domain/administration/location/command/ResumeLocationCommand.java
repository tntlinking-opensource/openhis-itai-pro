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

package com.openhis.health.domain.administration.location.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 恢复位置命令：从暂停状态恢复为正常可用（与启用/停用语义并列，由状态机区分）。
 */
@Getter
@Setter
public class ResumeLocationCommand extends BatchCommand<ResumeLocationCommand.ResumeLocationItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

    /** 单条恢复项。 */
    @Getter
    @Setter
    public static class ResumeLocationItemCommand {
        /** 位置主键。 */
        private Long locationId;
    }
}
