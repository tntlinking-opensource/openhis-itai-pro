package com.openhis.whale.booking.application.medtech.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 医技到检报到响应（7.6）。
 */
@Getter
@Setter
public class CheckInDiagnosticResponse {

    /** 预约主键。 */
    private Long appointmentId;

    /** 报到排队序号。 */
    private Integer displayOrder;
}
