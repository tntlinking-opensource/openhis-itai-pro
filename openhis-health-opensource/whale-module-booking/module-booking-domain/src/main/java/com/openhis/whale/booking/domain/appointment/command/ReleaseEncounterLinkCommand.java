/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.appointment.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 退号后释放预约与就诊关联命令（IF-REG-10）。
 */
@Getter
@Setter
public class ReleaseEncounterLinkCommand extends BatchCommand<ReleaseEncounterLinkCommand.ReleaseEncounterLinkItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    public static class ReleaseEncounterLinkItemCommand {

        /** 就诊主键。 */
        private Long encounterId;
    }
}
