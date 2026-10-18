/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.appointment.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 门诊预约回填就诊关联命令（IF-REG-07）。
 */
@Getter
@Setter
public class LinkEncounterCommand extends BatchCommand<LinkEncounterCommand.LinkEncounterItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    public static class LinkEncounterItemCommand {

        /** 预约主键。 */
        private Long appointmentId;

        /** 就诊主键。 */
        private Long encounterId;
    }
}
