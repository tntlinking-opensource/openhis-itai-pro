package com.openhis.whale.booking.application.bed.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 取消床位预留请求。
 */
@Getter
@Setter
public class CancelBedReservationRequest {

    @NotNull
    private Long appointmentId;

    private String reason;
}
