package com.openhis.whale.booking.application.medtech.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 创建医技预约响应。
 */
@Getter
@Setter
public class CreateDiagnosticBookingResponse {

    private Long appointmentId;
    private String busNo;
}
