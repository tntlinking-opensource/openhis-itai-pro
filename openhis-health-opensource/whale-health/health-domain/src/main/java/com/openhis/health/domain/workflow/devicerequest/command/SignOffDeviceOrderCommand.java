/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicerequest.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 签退耗材请求命令。
 */
@Getter
@Setter
public class SignOffDeviceOrderCommand extends BatchCommand<SignOffDeviceOrderCommand.SignOffDeviceOrderItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 签署off设备医嘱项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class SignOffDeviceOrderItemCommand {
        /** 主键。 */
        private Long id;
    }
}
