package com.openhis.whale.booking.domain.appointment.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 门诊预约改约命令：更新 slot 与时段快照（占释由应用层编排）。
 */
@Getter
@Setter
public class RescheduleOutpatientBookingCommand {

    /** 操作人标识。 */
    private Long operatorId;

    /** 待改约预约主键。 */
    private Long appointmentId;

    /** 目标号源主键。 */
    private Long newSlotId;

    /** 新时段计划开始。 */
    private java.time.LocalDateTime newStartInstant;

    /** 新时段计划结束。 */
    private java.time.LocalDateTime newEndInstant;

    /** 去向诊室主键。 */
    private Long destinationLocationId;
}
