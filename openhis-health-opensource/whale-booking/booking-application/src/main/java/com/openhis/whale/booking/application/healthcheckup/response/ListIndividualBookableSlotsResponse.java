package com.openhis.whale.booking.application.healthcheckup.response;

import com.openhis.whale.booking.application.register.response.BookableSlotDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ListIndividualBookableSlotsResponse {

    private List<BookableSlotDto> slots;
}
