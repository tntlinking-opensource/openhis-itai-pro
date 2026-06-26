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

    /** 待取消预约主键。 */
    @NotNull
    private Long appointmentId;

    /** 取消原因（可选）。 */
    private String reason;
}
