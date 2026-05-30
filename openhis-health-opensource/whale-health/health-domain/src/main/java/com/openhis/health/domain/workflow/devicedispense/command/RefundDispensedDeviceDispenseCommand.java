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

package com.openhis.health.domain.workflow.devicedispense.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 退耗材确认命令。
 */
@Getter
@Setter
public class RefundDispensedDeviceDispenseCommand extends BatchCommand<RefundDispensedDeviceDispenseCommand.RefundDispensedDeviceDispenseItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

/**
 * 退费dispensed设备发药项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class RefundDispensedDeviceDispenseItemCommand {
        /** 主键。 */
        private Long id;
        /** 退费数量。 */
        private BigDecimal refundQuantity;
        /** 退药执行人主键（待退确认路径写入 {@code performer_id}）。 */
        private Long practitionerId;
        /** 退药原因手写文本（可选；写入 {@code support_info}）。 */
        private String reasonText;
    }
}

