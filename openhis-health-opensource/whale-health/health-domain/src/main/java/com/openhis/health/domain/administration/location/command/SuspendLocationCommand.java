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
 * 暂停位置命令：临时挂起该地点服务（如消杀），需记录原因。
 */
@Getter
@Setter
public class SuspendLocationCommand extends BatchCommand<SuspendLocationCommand.SuspendLocationItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

    /** 单条暂停项。 */
    @Getter
    @Setter
    public static class SuspendLocationItemCommand {
        /** 位置主键。 */
        private Long locationId;
        /** 暂停原因。 */
        private String reason;
    }
}
