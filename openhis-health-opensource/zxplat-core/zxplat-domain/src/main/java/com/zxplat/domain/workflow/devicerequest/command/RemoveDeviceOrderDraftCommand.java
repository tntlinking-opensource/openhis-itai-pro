/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicerequest.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 删除耗材草稿命令。
 */
@Getter
@Setter
public class RemoveDeviceOrderDraftCommand extends BatchCommand<RemoveDeviceOrderDraftCommand.RemoveDeviceOrderDraftItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 移除设备医嘱草稿项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class RemoveDeviceOrderDraftItemCommand {
        /** 主键。 */
        private Long id;
    }
}
