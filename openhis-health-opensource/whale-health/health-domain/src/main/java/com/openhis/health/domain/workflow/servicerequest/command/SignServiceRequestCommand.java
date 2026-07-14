/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.servicerequest.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 签发服务申请命令。
 */
@Getter
@Setter
public class SignServiceRequestCommand extends BatchCommand<SignServiceRequestCommand.SignServiceRequestItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 签署服务请求项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class SignServiceRequestItemCommand {
        /** 主键。 */
        private Long id;
        /** signer主键。 */
        private Long signerId;
        /** 签署时间。 */
        private LocalDateTime signTime;
    }
}
