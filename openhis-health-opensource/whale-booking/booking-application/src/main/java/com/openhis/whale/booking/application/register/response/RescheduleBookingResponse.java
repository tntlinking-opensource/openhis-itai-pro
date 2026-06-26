package com.openhis.whale.booking.application.register.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 改约响应（4.4）。
 */
@Getter
@Setter
public class RescheduleBookingResponse {

    /** 改约后的预约主键。 */
    private Long appointmentId;

    /** 改约后的号源主键。 */
    private Long newSlotId;
}
