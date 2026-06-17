package com.openhis.whale.booking.domain.appointment.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 门诊预约改约命令：更新 slot 与时段快照（占释由应用层编排）。
 */
@Getter
@Setter
public class RescheduleOutpatientBookingCommand {

    private Long operatorId;
    private Long appointmentId;
    private Long newSlotId;
    private java.time.LocalDateTime newStartInstant;
    private java.time.LocalDateTime newEndInstant;
    private Long destinationLocationId;
}
