package com.openhis.whale.booking.application.register.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RescheduleBookingResponse {

    private Long appointmentId;
    private Long newSlotId;
}
