/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicerequest.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 执行确认命令。
 */
@Getter
@Setter
public class ExecuteDeviceOrderCommand extends BatchCommand<ExecuteDeviceOrderCommand.ExecuteDeviceOrderItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * execute设备医嘱项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class ExecuteDeviceOrderItemCommand {
        /** 主键。 */
        private Long id;
        /** 执行人主键。 */
        private Long performerId;
        /** 执行机构主键。 */
        private Long performOrgId;
    }
}
