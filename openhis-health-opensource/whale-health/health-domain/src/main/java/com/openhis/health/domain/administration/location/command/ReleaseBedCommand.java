/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.location.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 释放床位命令：解除床位与就诊的占用关系（出院或转床前置）。
 */
@Getter
@Setter
public class ReleaseBedCommand extends BatchCommand<ReleaseBedCommand.ReleaseBedItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

    /** 单条释放项。 */
    @Getter
    @Setter
    public static class ReleaseBedItemCommand {
        /** 床位位置主键。 */
        private Long locationId;
        /** 原占用就诊主键。 */
        private Long encounterId;
    }
}
