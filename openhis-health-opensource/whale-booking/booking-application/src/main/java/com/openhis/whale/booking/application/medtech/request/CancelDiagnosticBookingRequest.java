package com.openhis.whale.booking.application.medtech.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 取消医技预约请求。
 */
@Getter
@Setter
public class CancelDiagnosticBookingRequest {

    @NotNull
    private Long appointmentId;

    private String reason;
}
