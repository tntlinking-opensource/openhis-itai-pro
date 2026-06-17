package com.openhis.whale.booking.application.policy.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveRegistrationPolicyRequest {

    private Long id;

    @NotNull
    private Long organizationId;

    @NotBlank
    private String walkInSlotMode;

    @NotNull
    private Boolean autoCheckInOnRegister;

    @NotBlank
    private String onCancelPolicy;

    @NotNull
    private Boolean allowEditPractitionerFromAppointment;
}
