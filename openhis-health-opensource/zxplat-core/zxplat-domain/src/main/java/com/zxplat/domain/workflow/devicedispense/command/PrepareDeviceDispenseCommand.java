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
 * 拣配耗材命令。
 */
@Getter
@Setter
public class PrepareDeviceDispenseCommand extends BatchCommand<PrepareDeviceDispenseCommand.PrepareDeviceDispenseItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

/**
 * prepare设备发药项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class PrepareDeviceDispenseItemCommand {
        /** 主键。 */
        private Long id;
        /** preparer主键。 */
        private Long preparerId;
        /** 批号号。 */
        private String lotNumber;
        /** 追溯号。 */
        private String traceNo;
        /** 数量。 */
        private BigDecimal quantity;
    }
}
