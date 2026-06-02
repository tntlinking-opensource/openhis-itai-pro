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

/**
 * 门诊药房拒绝药品退药（待退药态推进为已拒绝）命令。
 */
@Getter
@Setter
public class DeclinePendingRefundMedicationDispenseCommand extends BatchCommand<DeclinePendingRefundMedicationDispenseCommand.Item> {

    /** 操作人标识（批量条目共用）。 */
    private Long operatorId;

    /**
     * 单条拒绝子命令。
     */
    @Getter
    @Setter
    public static class Item {
        /** 发放单标识。 */
        private Long id;
        /** 拒绝原因（手写文本，落 {@code not_performed_reason_enum} 列承载展示语义）。 */
        private String declineReasonText;
        /** 执行药师主键。 */
        private Long practitionerId;
    }
}
