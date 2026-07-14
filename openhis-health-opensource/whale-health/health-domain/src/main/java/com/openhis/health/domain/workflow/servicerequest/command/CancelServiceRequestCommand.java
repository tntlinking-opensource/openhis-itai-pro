/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.servicerequest.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 取消服务申请命令。
 */
@Getter
@Setter
public class CancelServiceRequestCommand extends BatchCommand<CancelServiceRequestCommand.CancelServiceRequestItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 取消服务请求项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class CancelServiceRequestItemCommand {
        /** 主键。 */
        private Long id;
        /** 取消原因。 */
        private String cancelReason;
    }
}
