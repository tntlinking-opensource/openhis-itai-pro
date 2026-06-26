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

    /** 机构/科室主键。 */
    @NotNull
    private Long organizationId;

    /** 渠道编码。 */
    @NotBlank
    private String channelCode;

    /** 患者主键（与 appointmentBusNo 二选一）。 */
    private Long patientId;

    /** 预约业务流水号（与 patientId 二选一）。 */
    private String appointmentBusNo;
}
