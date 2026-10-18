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
 * 执行路径直发（已发放落库）命令。
 */
@Getter
@Setter
public class GenerateIssuedDeviceDispenseFromExecutionCommand extends BatchCommand<GenerateIssuedDeviceDispenseFromExecutionCommand.GenerateIssuedDeviceDispenseFromExecutionItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 生成已发设备发药来源执行实例项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class GenerateIssuedDeviceDispenseFromExecutionItemCommand {
        /** 设备申请主键。 */
        private Long deviceReqId;
        /** 操作主键。 */
        private Long procedureId;
        /** 批号号。 */
        private String lotNumber;
        /** 追溯号。 */
        private String traceNo;
        /** 发药数量。 */
        private BigDecimal dispenseQuantity;
    }
}
