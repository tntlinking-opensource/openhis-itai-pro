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

package com.openhis.health.domain.administration.devicedefinition.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 删除器材（耗材）定义命令（逻辑删除）。
 */
@Getter
@Setter
public class DeleteDeviceDefinitionsCommand extends BatchCommand<DeleteDeviceDefinitionsCommand.Item> {
    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 批量命令明细项。
     */
    @Getter
    @Setter
    public static class Item {
        /** 待删除目录标识。 */
        private Long id;
    }
}

