/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.appointment.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 渠道报到入队命令。
 */
@Getter
@Setter
public class CheckInToQueueCommand extends BatchCommand<CheckInToQueueCommand.CheckInToQueueItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

/**
 * 检查into队列项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class CheckInToQueueItemCommand {
        /** 预约kind。 */
        private String appointmentKind;
        /** 机构主键。 */
        private Long organizationId;
        /** 去向诊室。 */
        private Long destinationLocationId;
        /** 就诊主键。 */
        private Long encounterId;
        /** 患者主键。 */
        private Long patientId;
        /** 渠道来源（会话、设备等审计信息）。 */
        private CreateAppointmentCommand.AppointmentOriginCommand origin;
    }
}
