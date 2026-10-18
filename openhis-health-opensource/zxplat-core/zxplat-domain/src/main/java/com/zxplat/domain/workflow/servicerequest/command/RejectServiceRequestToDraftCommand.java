/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.servicerequest.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 退回至草稿命令。
 */
@Getter
@Setter
public class RejectServiceRequestToDraftCommand extends BatchCommand<RejectServiceRequestToDraftCommand.RejectServiceRequestToDraftItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;
    /** 校对人主键（护士退回追溯）。 */
    private Long performerCheckId;
    /** 校对/退回时间。 */
    private LocalDateTime checkTime;

/**
 * 驳回服务请求to草稿项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class RejectServiceRequestToDraftItemCommand {
        /** 主键。 */
        private Long id;
        /** 原因。 */
        private String reason;
    }
}
