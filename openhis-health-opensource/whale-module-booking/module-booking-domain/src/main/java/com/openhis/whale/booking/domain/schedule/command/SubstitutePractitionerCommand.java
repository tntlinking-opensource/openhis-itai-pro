/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.schedule.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 替诊命令（CHG-SCH-08 · 2.21）。
 */
@Getter
@Setter
public class SubstitutePractitionerCommand extends BatchCommand<SubstitutePractitionerCommand.SubstitutePractitionerItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    @Getter
    @Setter
    public static class SubstitutePractitionerItemCommand {

        /** 日程主键。 */
        private Long scheduleId;

        /** 替诊医师；空表示撤销替诊。 */
        private Long substitutePractitionerId;

        /** 替诊原因。 */
        private String reason;
    }
}
