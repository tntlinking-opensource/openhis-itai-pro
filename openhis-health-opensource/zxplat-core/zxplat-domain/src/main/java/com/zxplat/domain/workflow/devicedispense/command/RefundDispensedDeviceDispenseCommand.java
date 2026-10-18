/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicedispense.command;

import com.zxplat.domain.share.command.BatchCommand;
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
