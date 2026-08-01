/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.location.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 关闭库房库存期初命令（GAP-OPN-002 · {@code closeInventoryOpening}）。
 */
@Getter
@Setter
public class CloseInventoryOpeningCommand extends BatchCommand<CloseInventoryOpeningCommand.CloseInventoryOpeningItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 关闭项。
     */
    @Getter
    @Setter
    public static class CloseInventoryOpeningItemCommand {
        /** 目标库房位置主键。 */
        private Long locationId;
    }
}
