/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicedispense.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 核对发放命令。
 */
@Getter
@Setter
public class DispenseDeviceCommand extends BatchCommand<DispenseDeviceCommand.DispenseDeviceItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

/**
 * 发药设备项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class DispenseDeviceItemCommand {
        /** 主键。 */
        private Long id;
        /** 执行人主键。 */
        private Long performerId;
        /** 发药数量。 */
        private BigDecimal dispenseQuantity;
    }
}
