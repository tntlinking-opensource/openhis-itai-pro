package com.openhis.whale.booking.application.channel.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 渠道创建预约响应（10.2）。
 */
@Getter
@Setter
public class ChannelCreateBookingResponse {

    /** 预约主键。 */
    private Long appointmentId;

    /** 预约业务流水号。 */
    private String busNo;
}
