package com.openhis.whale.booking.application.medtech.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 医技改约响应（7.5）。
 */
@Getter
@Setter
public class RescheduleDiagnosticBookingResponse {

    private Long appointmentId;

    private Long newSlotId;
}
