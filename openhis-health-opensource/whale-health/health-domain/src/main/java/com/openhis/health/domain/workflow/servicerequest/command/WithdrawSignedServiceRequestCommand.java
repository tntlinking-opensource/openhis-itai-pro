/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.servicerequest.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 签退服务申请命令。
 */
@Getter
@Setter
public class WithdrawSignedServiceRequestCommand extends BatchCommand<WithdrawSignedServiceRequestCommand.WithdrawSignedServiceRequestItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 撤回已签署服务请求项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class WithdrawSignedServiceRequestItemCommand {
        /** 主键。 */
        private Long id;
    }
}
