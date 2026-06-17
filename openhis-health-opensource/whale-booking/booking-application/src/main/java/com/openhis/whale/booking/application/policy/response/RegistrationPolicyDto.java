package com.openhis.whale.booking.application.policy.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistrationPolicyDto {

    private Long id;
    private Long organizationId;
    private String walkInSlotMode;
    private Boolean autoCheckInOnRegister;
    private String onCancelPolicy;
    private Boolean allowEditPractitionerFromAppointment;
}
