/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdispense.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 门诊退费申请撤销：将因申请而拒绝的未发药发放恢复为待发药命令。
 *
 * <p>仅当状态为 {@code DECLINED} 且未执行原因与期望码一致时允许恢复为 {@code PREPARATION}。</p>
 */
@Getter
@Setter
public class RestoreDeclinedMedicationDispenseAfterRefundRevokeCommand
    extends BatchCommand<RestoreDeclinedMedicationDispenseAfterRefundRevokeCommand.Item> {

    /** 操作人标识（批量条目共用；本写路径仅审计编排侧使用）。 */
    private Long operatorId;

    /**
     * 单条恢复子命令。
     */
    @Getter
    @Setter
    public static class Item {
        /** 发放单主键（须为因门诊退费申请而 DECLINED 的未发药行）。 */
        private Long id;
        /**
         * 期望的未执行原因码；须与行上 {@code not_performed_reason_enum} 一致
         *（通常为 {@code OUTPATIENT_REFUND_APPLICATION}）。
         */
        private String expectedNotPerformedReasonEnum;
    }
}
