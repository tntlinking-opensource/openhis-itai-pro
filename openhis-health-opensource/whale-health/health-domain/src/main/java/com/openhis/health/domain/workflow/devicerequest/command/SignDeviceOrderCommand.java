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

package com.openhis.health.domain.workflow.devicerequest.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 签发耗材请求命令。
 */
@Getter
@Setter
public class SignDeviceOrderCommand extends BatchCommand<SignDeviceOrderCommand.SignDeviceOrderItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 签署设备医嘱项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class SignDeviceOrderItemCommand {
        /** 主键。 */
        private Long id;
    }
}

