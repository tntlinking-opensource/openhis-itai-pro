/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.location.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 停用位置命令：地点暂时不可用，需记录原因（如维修、封控）。
 */
@Getter
@Setter
public class DeactivateLocationCommand extends BatchCommand<DeactivateLocationCommand.DeactivateLocationItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

    /** 单条停用项。 */
    @Getter
    @Setter
    public static class DeactivateLocationItemCommand {
        /** 位置主键。 */
        private Long locationId;
        /** 停用原因。 */
        private String reason;
    }
}
