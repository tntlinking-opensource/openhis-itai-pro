package com.openhis.whale.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateIndividualBookingResponse {

    private Long appointmentId;
    private String busNo;
}
