/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.servicerequest.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 删除服务申请草稿命令。
 */
@Getter
@Setter
public class RemoveServiceRequestDraftCommand extends BatchCommand<RemoveServiceRequestDraftCommand.RemoveServiceRequestDraftItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 移除服务请求草稿项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class RemoveServiceRequestDraftItemCommand {
        /** 主键。 */
        private Long id;
    }
}
