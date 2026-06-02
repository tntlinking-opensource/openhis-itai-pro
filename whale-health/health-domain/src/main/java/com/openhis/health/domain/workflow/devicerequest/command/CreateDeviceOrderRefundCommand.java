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

import java.math.BigDecimal;

/**
 * 申请退耗材命令。
 */
@Getter
@Setter
public class CreateDeviceOrderRefundCommand extends BatchCommand<CreateDeviceOrderRefundCommand.CreateDeviceOrderRefundItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 创建设备医嘱退费项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class CreateDeviceOrderRefundItemCommand {
        /** source请求主键。 */
        private Long sourceRequestId;
        /** 退费数量。 */
        private BigDecimal refundQuantity;
        /** 原因。 */
        private String reason;
    }
}

