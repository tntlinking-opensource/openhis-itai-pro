package com.openhis.whale.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 体检到检签到响应（9.9）。
 */
@Getter
@Setter
public class CheckInHealthCheckupResponse {

    /** 预约主键。 */
    private Long appointmentId;

    /** 队列序号。 */
    private Integer displayOrder;
}
