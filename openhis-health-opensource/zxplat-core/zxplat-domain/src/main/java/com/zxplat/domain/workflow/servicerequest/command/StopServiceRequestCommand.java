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
 * 停止服务申请命令。
 */
@Getter
@Setter
public class StopServiceRequestCommand extends BatchCommand<StopServiceRequestCommand.StopServiceRequestItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 停止服务请求项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class StopServiceRequestItemCommand {
        /** 主键。 */
        private Long id;
        /** 停止时间。 */
        private LocalDateTime stopTime;
        /** 原因。 */
        private String reason;
    }
}
