package com.openhis.whale.booking.application.medtech.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 医技可约号源查询请求。
 */
@Getter
@Setter
public class ListDiagnosticBookableSlotsRequest {

    @NotNull
    private Long organizationId;

    @NotNull
    private Long serviceRequestId;

    @NotNull
    private LocalDate date;
}
