package com.openhis.whale.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 创建个检预约响应（6.x 旧版 API 别名，对应 CreateIndividualHealthCheckupBookingResponse）。
 */
@Getter
@Setter
public class CreateIndividualBookingResponse {

    /** 预约主键。 */
    private Long appointmentId;

    /** 预约业务流水号。 */
    private String busNo;
}
