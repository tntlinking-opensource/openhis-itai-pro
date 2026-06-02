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

package com.openhis.health.domain.medication.medicationdispense.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 药品发放退药命令。
 */
@Getter
@Setter
public class RefundDispensedMedicationCommand extends BatchCommand<RefundDispensedMedicationCommand.Item> {
    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 退药子命令。
     */
    @Getter
    @Setter
    public static class Item {
        /** 发放单标识。 */
        private Long id;
        /** 退药数量。 */
        private BigDecimal refundQuantity;
        /** 子命令操作人标识。 */
        private Long operatorId;
        /** 退药执行药师主键（待退药确认路径写入 {@code practitioner_id}）。 */
        private Long practitionerId;
        /** 退药原因手写文本（可选；写入 {@code support_info} 承载展示）。 */
        private String reasonText;
    }
}
