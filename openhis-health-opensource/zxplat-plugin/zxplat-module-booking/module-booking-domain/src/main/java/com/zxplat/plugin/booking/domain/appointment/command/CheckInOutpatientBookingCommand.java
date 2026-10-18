/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.appointment.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 门诊预约报到入队命令（5.1 · 预约转候诊）。
 */
@Getter
@Setter
public class CheckInOutpatientBookingCommand extends BatchCommand<CheckInOutpatientBookingCommand.CheckInOutpatientBookingItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    public static class CheckInOutpatientBookingItemCommand {
        /** 预约主键。 */
        private Long appointmentId;
        /** 机构主键。 */
        private Long organizationId;
        /** 去向诊室（可选，覆盖预约原值）。 */
        private Long destinationLocationId;
    }
}
