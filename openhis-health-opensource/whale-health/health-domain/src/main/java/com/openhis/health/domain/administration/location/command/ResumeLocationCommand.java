/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.location.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 恢复位置命令：从暂停状态恢复为正常可用（与启用/停用语义并列，由状态机区分）。
 */
@Getter
@Setter
public class ResumeLocationCommand extends BatchCommand<ResumeLocationCommand.ResumeLocationItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

    /** 单条恢复项。 */
    @Getter
    @Setter
    public static class ResumeLocationItemCommand {
        /** 位置主键。 */
        private Long locationId;
    }
}
