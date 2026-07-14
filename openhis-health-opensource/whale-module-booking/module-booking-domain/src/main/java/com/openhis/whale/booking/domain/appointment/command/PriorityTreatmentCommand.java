/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.appointment.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 优先就诊命令。
 */
@Getter
@Setter
public class PriorityTreatmentCommand extends BatchCommand<PriorityTreatmentCommand.PriorityTreatmentItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

/**
 * 优先诊疗项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class PriorityTreatmentItemCommand {
        /** 预约主键。 */
        private Long appointmentId;
        /** destination位置主键。 */
        private Long destinationLocationId;
    }
}
