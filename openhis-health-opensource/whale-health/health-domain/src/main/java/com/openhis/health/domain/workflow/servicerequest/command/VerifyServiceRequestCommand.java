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
 * 校对服务申请命令。
 */
@Getter
@Setter
public class VerifyServiceRequestCommand extends BatchCommand<VerifyServiceRequestCommand.VerifyServiceRequestItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 核对服务请求项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class VerifyServiceRequestItemCommand {
        /** 主键。 */
        private Long id;
        /** 执行人检查主键。 */
        private Long performerCheckId;
        /** 检查时间。 */
        private LocalDateTime checkTime;
        /** 检查结果。 */
        private String checkResult;
    }
}
