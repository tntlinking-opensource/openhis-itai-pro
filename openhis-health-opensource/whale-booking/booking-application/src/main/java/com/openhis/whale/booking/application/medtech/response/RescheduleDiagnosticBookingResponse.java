package com.openhis.whale.booking.application.medtech.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 医技改约响应（7.5）。
 */
@Getter
@Setter
public class RescheduleDiagnosticBookingResponse {

    /** 改约后预约主键。 */
    private Long appointmentId;

    /** 改约后号源主键。 */
    private Long newSlotId;
}
