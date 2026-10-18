/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.location.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 预留床位命令：为待定入院等场景预留床位并记录原因。
 */
@Getter
@Setter
public class ReserveBedCommand extends BatchCommand<ReserveBedCommand.ReserveBedItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

    /** 单条预留项。 */
    @Getter
    @Setter
    public static class ReserveBedItemCommand {
        /** 床位位置主键。 */
        private Long locationId;
        /** 预留原因。 */
        private String reason;
    }
}
