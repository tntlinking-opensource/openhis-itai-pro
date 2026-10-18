/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.slot.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 同步号源时段外部标识命令（DM-WKF-010）。
 */
@Getter
@Setter
public class SyncSlotIdentifiersCommand extends BatchCommand<SyncSlotIdentifiersCommand.SyncSlotIdentifiersItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    public static class SyncSlotIdentifiersItemCommand {
        /** 号源时段标识。 */
        private Long slotId;

        /** 标识集合（以该集合为准进行全量对齐）。 */
        private List<SlotIdentifierItemCommand> identifiers;
    }

    @Getter
    @Setter
    public static class SlotIdentifierItemCommand {
        /** 标识体系。 */
        private String idSystem;

        /** 标识值。 */
        private String idValue;

        /** 用途编码（可选）。 */
        private String useCode;
    }
}
