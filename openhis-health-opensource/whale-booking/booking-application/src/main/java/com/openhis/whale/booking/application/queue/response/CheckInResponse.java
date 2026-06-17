package com.openhis.whale.booking.application.queue.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 报到入队响应（5.1）。
 */
@Getter
@Setter
public class CheckInResponse {

    /** 候诊项主键。 */
    private Long appointmentId;

    /** 业务单号。 */
    private String busNo;

    /** 队列排序。 */
    private Integer displayOrder;

    /** 预约分类。 */
    private String appointmentKind;
}
