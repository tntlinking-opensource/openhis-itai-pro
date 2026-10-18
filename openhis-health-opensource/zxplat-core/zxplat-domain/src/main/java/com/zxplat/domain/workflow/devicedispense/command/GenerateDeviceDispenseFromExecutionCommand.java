/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicedispense.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 执行路径生成待发发放单命令。
 */
@Getter
@Setter
public class GenerateDeviceDispenseFromExecutionCommand extends BatchCommand<GenerateDeviceDispenseFromExecutionCommand.GenerateDeviceDispenseFromExecutionItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 生成设备发药来源执行实例项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class GenerateDeviceDispenseFromExecutionItemCommand {
        /** 设备申请主键。 */
        private Long deviceReqId;
        /** 操作主键。 */
        private Long procedureId;
        /** 位置主键。 */
        private Long locationId;
        /** planned发药时间。 */
        private LocalDateTime plannedDispenseTime;
    }
}
