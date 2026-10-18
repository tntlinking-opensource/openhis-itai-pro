/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.location.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 启用位置命令：恢复地点为可选用状态（与停用/暂停区分，由领域状态机解释）。
 */
@Getter
@Setter
public class ActivateLocationCommand extends BatchCommand<ActivateLocationCommand.ActivateLocationItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

    /** 单条启用项。 */
    @Getter
    @Setter
    public static class ActivateLocationItemCommand {
        /** 位置主键。 */
        private Long locationId;
    }
}
