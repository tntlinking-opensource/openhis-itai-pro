/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicedispense.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 作废未发命令。
 */
@Getter
@Setter
public class CancelUndispensedDeviceDispenseCommand extends BatchCommand<CancelUndispensedDeviceDispenseCommand.CancelUndispensedDeviceDispenseItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

/**
 * 取消未发设备发药项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class CancelUndispensedDeviceDispenseItemCommand {
        /** 主键。 */
        private Long id;
        /** notperformed原因枚举编码。 */
        private String notPerformedReasonEnum;
    }
}
