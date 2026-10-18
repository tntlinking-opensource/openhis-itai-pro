/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.servicerequest.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 申请退诊疗（创建负向服务申请）命令。
 */
@Getter
@Setter
public class CreateServiceOrderRefundCommand extends BatchCommand<CreateServiceOrderRefundCommand.CreateServiceOrderRefundItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 创建退诊疗负向申请单行命令。
     */
    @Getter
    @Setter
    public static class CreateServiceOrderRefundItemCommand {
        /** 原服务申请主键。 */
        private Long sourceRequestId;
        /** 拟退数量（正数，落库时取反）。 */
        private BigDecimal refundQuantity;
        /** 退诊疗原因。 */
        private String reason;
    }
}
