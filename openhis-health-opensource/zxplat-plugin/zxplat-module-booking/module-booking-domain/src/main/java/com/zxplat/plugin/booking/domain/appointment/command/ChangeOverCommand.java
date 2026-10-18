/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.appointment.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 前后调位命令。
 */
@Getter
@Setter
public class ChangeOverCommand extends BatchCommand<ChangeOverCommand.ChangeOverItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

/**
 * 变更交班项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class ChangeOverItemCommand {
        /** 预约主键。 */
        private Long appointmentId;
        /** 分诊等级。 */
        private String triageLevel;
        /** moveforward。 */
        private Boolean moveForward;
    }
}
