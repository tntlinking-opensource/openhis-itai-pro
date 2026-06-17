package com.openhis.whale.booking.application.medtech.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建医技预约请求。
 */
@Getter
@Setter
public class CreateDiagnosticBookingRequest {

    @NotNull
    private Long organizationId;

    @NotNull
    private Long patientId;

    @NotNull
    private Long serviceRequestId;

    @NotNull
    private Long slotId;

    private Long encounterId;

    private String originChannel;
}
