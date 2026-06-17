package com.openhis.whale.booking.application.policy.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GetRegistrationModeResponse {

    private String walkInSlotMode;
    private Boolean bookingEnabled;
}
