package com.openhis.whale.booking.application.register.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 可约号源列表响应。
 */
@Getter
@Setter
public class ListBookableSlotsResponse {

    /** 可约时段列表。 */
    private List<BookableSlotDto> slots;
}
