package com.openhis.whale.booking.application.channel.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 渠道创建预约请求（10.2）。
 */
@Getter
@Setter
public class ChannelCreateBookingRequest {

    @NotNull
    private Long organizationId;

    @NotBlank
    private String channelCode;

    @NotBlank
    private String appointmentKind;

    @NotNull
    private Long patientId;

    @NotNull
    private Long slotId;

    private Long packageId;

    private Long serviceRequestId;
}
