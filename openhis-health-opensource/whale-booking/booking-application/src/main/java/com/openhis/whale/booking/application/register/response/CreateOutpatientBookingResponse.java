package com.openhis.whale.booking.application.register.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 创建预约响应（4.2）。
 */
@Getter
@Setter
public class CreateOutpatientBookingResponse {

    /** 预约主键。 */
    private Long appointmentId;

    /** 预约业务单号。 */
    private String busNo;
}
