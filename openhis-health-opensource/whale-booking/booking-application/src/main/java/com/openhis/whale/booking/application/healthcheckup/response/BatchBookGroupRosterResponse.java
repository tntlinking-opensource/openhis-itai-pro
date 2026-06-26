package com.openhis.whale.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 团检批量预约响应（6.x 旧版 API 别名，对应 BatchCreateHealthCheckupBookingResponse）。
 */
@Getter
@Setter
public class BatchBookGroupRosterResponse {

    /** 已成功预约人数。 */
    private Integer bookedCount;
}
