package com.openhis.whale.booking.application.channel.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 渠道创建预约响应。
 */
@Getter
@Setter
public class ChannelCreateBookingResponse {

    private Long appointmentId;

    private String busNo;
}
