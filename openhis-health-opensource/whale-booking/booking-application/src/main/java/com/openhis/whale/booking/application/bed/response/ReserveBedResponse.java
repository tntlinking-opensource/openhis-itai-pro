package com.openhis.whale.booking.application.bed.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 床位预留响应。
 */
@Getter
@Setter
public class ReserveBedResponse {

    private Long appointmentId;
    private String busNo;
}
