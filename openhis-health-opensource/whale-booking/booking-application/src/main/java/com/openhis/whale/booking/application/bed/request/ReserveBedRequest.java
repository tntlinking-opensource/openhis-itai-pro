package com.openhis.whale.booking.application.bed.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 床位预留请求。
 */
@Getter
@Setter
public class ReserveBedRequest {

    @NotNull
    private Long organizationId;

    @NotNull
    private Long bedLocationId;

    @NotNull
    private Long patientId;

    @NotNull
    private LocalDate intendedAdmissionDate;

    private String commentText;
}
