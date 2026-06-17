package com.openhis.whale.booking.application.register.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 取消预约响应（含候补自动提升信息）。
 */
@Getter
@Setter
public class CancelBookingResponse {

    /** 是否触发了候补提升。 */
    private Boolean waitlistPromoted;

    /** 提升产生的预约主键。 */
    private Long promotedAppointmentId;

    /** 提升产生的预约 busNo。 */
    private String promotedBusNo;
}
