package com.openhis.whale.booking.application.channel.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 渠道查询预约请求（10.2）。
 */
@Getter
@Setter
public class ChannelQueryBookingRequest {

    @NotNull
    private Long organizationId;

    @NotBlank
    private String channelCode;

    private Long patientId;

    private String appointmentBusNo;
}
