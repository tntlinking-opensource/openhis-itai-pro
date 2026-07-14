/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
