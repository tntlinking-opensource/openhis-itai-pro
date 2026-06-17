package com.openhis.whale.booking.application.medtech.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 就诊检索请求（医技预约）。
 */
@Getter
@Setter
public class SearchEncounterRequest {

    @NotNull
    private Long organizationId;

    private String keyword;

    private String encounterBusNo;

    private Long patientId;
}
