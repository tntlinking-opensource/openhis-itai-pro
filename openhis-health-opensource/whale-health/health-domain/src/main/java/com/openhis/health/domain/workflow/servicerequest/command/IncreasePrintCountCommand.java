/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.servicerequest.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 增加打印次数命令。
 */
@Getter
@Setter
public class IncreasePrintCountCommand extends BatchCommand<IncreasePrintCountCommand.IncreasePrintCountItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 递增打印次数项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class IncreasePrintCountItemCommand {
        /** 主键。 */
        private Long id;
        /** 递增by。 */
        private Integer increaseBy;
    }
}
