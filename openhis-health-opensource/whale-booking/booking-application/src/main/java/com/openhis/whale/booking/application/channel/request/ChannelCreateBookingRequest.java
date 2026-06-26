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

    /** 机构/科室主键。 */
    @NotNull
    private Long organizationId;

    /** 渠道编码。 */
    @NotBlank
    private String channelCode;

    /** 预约类型。 */
    @NotBlank
    private String appointmentKind;

    /** 患者主键。 */
    @NotNull
    private Long patientId;

    /** 号源主键。 */
    @NotNull
    private Long slotId;

    /** 体检套餐主键（体检预约时必填）。 */
    private Long packageId;

    /** 医嘱主键（医技预约时可选）。 */
    private Long serviceRequestId;
}
