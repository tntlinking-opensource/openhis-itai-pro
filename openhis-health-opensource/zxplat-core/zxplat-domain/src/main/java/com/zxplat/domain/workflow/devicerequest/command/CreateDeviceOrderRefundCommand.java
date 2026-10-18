/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicerequest.command;

import com.zxplat.domain.share.command.BatchCommand;
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
