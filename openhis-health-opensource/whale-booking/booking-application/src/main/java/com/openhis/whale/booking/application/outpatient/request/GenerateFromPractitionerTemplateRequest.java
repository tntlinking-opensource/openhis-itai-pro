package com.openhis.whale.booking.application.outpatient.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class GenerateFromPractitionerTemplateRequest {

    @NotNull
    private Long organizationId;

    @NotNull
    private Long practitionerId;

    /** 目标周起始日（可选，默认下周一）。 */
    private LocalDate weekStart;
}
