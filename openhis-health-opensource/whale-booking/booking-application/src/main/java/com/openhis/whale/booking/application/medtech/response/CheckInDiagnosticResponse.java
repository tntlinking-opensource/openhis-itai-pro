package com.openhis.whale.booking.application.medtech.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 医技到检报到响应（7.6）。
 */
@Getter
@Setter
public class CheckInDiagnosticResponse {

    private Long appointmentId;

    private Integer displayOrder;
}
