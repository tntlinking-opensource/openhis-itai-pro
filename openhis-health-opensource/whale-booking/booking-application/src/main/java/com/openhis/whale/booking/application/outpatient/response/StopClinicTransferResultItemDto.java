package com.openhis.whale.booking.application.outpatient.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 停诊转交执行行结果。
 */
@Getter
@Setter
public class StopClinicTransferResultItemDto {

    /** 预约主键。 */
    private Long appointmentId;

    /** 目标号源主键。 */
    private Long targetSlotId;

    /** 结果说明。 */
    private String message;
}
