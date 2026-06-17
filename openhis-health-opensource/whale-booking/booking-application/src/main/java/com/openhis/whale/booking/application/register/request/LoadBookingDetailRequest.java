package com.openhis.whale.booking.application.register.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 预约详情请求（4.6）。
 */
@Getter
@Setter
public class LoadBookingDetailRequest {

    /** 预约主键。 */
    @NotNull
    private Long appointmentId;
}
