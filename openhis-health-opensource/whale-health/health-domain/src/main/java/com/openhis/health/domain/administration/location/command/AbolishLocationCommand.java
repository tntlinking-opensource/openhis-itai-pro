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
 * 废弃位置命令：将地点/床位等资源标记为废止，不再参与业务选用。
 */
@Getter
@Setter
public class AbolishLocationCommand extends BatchCommand<AbolishLocationCommand.AbolishLocationItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

    /** 单条废弃项。 */
    @Getter
    @Setter
    public static class AbolishLocationItemCommand {
        /** 位置主键。 */
        private Long locationId;
    }
}
