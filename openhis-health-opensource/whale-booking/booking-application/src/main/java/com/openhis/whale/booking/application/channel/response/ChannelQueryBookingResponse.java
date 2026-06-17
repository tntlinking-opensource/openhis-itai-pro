package com.openhis.whale.booking.application.channel.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 渠道查询预约响应。
 */
@Getter
@Setter
public class ChannelQueryBookingResponse {

    private Long appointmentId;

    private String busNo;

    private String appointmentKind;

    private Long patientId;

    private Long slotId;

    private LocalDateTime startInstant;

    private LocalDateTime endInstant;

    private String statusView;
}
