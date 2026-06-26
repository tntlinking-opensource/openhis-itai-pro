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

    /** 机构/科室主键。 */
    @NotNull
    private Long organizationId;

    /** 渠道编码。 */
    @NotBlank
    private String channelCode;

    /** 待取消预约主键。 */
    @NotNull
    private Long appointmentId;

    /** 取消原因（可选）。 */
    private String reason;
}
