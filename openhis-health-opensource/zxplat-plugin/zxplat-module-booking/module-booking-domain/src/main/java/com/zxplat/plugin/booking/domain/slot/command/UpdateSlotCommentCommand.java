/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.slot.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新时段备注命令（DM-WKF-010）。
 */
@Getter
@Setter
public class UpdateSlotCommentCommand extends BatchCommand<UpdateSlotCommentCommand.UpdateSlotCommentItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    public static class UpdateSlotCommentItemCommand {
        /** 号源时段标识。 */
        private Long slotId;

        /** 备注文本（可选）。 */
        private String commentText;
    }
}
