/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
