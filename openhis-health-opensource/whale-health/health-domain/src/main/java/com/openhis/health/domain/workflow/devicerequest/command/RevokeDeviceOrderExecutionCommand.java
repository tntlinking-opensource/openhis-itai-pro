/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicerequest.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 撤销执行命令。
 */
@Getter
@Setter
public class RevokeDeviceOrderExecutionCommand extends BatchCommand<RevokeDeviceOrderExecutionCommand.RevokeDeviceOrderExecutionItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * revoke设备医嘱执行实例项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class RevokeDeviceOrderExecutionItemCommand {
        /** 主键。 */
        private Long id;
    }
}
