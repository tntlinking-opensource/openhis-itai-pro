package com.openhis.whale.booking.application.channel.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 渠道取消预约请求（10.2）。
 */
@Getter
@Setter
public class ChannelCancelBookingRequest {

    @NotNull
    private Long organizationId;

    @NotBlank
    private String channelCode;

    @NotNull
    private Long appointmentId;

    private String reason;
}
