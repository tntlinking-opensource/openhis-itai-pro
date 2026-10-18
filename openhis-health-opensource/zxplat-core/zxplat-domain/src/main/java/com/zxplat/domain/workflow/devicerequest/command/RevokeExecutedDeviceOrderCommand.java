/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicerequest.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 撤销已完成请求命令（对执行侧直写完成态进行撤销）。
 */
@Getter
@Setter
public class RevokeExecutedDeviceOrderCommand extends BatchCommand<RevokeExecutedDeviceOrderCommand.RevokeExecutedDeviceOrderItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * revoke已执行设备医嘱项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class RevokeExecutedDeviceOrderItemCommand {
        /** 主键。 */
        private Long id;
    }
}
