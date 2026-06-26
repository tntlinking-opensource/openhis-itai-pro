package com.openhis.whale.booking.application.register.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 改约请求（4.4）。
 */
@Getter
@Setter
public class RescheduleBookingRequest {

    /** 待改约预约主键。 */
    @NotNull
    private Long appointmentId;

    /** 目标号源主键。 */
    @NotNull
    private Long newSlotId;

    /** 去向诊室（可选，缺省时从目标 Schedule 参与者解析）。 */
    private Long destinationLocationId;
}
