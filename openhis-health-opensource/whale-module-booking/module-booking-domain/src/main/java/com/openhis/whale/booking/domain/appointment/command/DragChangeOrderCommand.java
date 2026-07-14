/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.appointment.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 拖拽调位命令。
 */
@Getter
@Setter
public class DragChangeOrderCommand extends BatchCommand<DragChangeOrderCommand.DragChangeOrderItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

/**
 * 拖拽变更医嘱项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class DragChangeOrderItemCommand {
        /** 预约主键。 */
        private Long appointmentId;
        /** 目标index。 */
        private Integer targetIndex;
    }
}
