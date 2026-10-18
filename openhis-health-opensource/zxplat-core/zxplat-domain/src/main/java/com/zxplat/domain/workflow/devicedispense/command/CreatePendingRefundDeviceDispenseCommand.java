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
 * 创建待退耗材发放记录命令。
 */
@Getter
@Setter
public class CreatePendingRefundDeviceDispenseCommand extends BatchCommand<CreatePendingRefundDeviceDispenseCommand.CreatePendingRefundDeviceDispenseItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 创建待处理退费设备发药项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class CreatePendingRefundDeviceDispenseItemCommand {
        /** source发药主键。 */
        private Long sourceDispenseId;
        /** 拟退数量。 */
        private BigDecimal refundQuantity;
        /** 负向耗材申请主键（门诊申请退费路径须传入；未传则沿用源发放 device_req_id）。 */
        private Long refundDeviceRequestId;
    }
}
