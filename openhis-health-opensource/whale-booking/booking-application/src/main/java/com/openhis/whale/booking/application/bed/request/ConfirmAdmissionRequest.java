package com.openhis.whale.booking.application.bed.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 入院确认请求（8.5）。
 */
@Getter
@Setter
public class ConfirmAdmissionRequest {

    @NotNull
    private Long appointmentId;

    private String reason;
}
