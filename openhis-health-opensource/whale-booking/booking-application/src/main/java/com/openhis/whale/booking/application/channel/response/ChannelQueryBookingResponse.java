package com.openhis.whale.booking.application.channel.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 渠道查询预约响应（10.2）。
 */
@Getter
@Setter
public class ChannelQueryBookingResponse {

    /** 预约主键。 */
    private Long appointmentId;

    /** 预约业务流水号。 */
    private String busNo;

    /** 预约类型。 */
    private String appointmentKind;

    /** 患者主键。 */
    private Long patientId;

    /** 号源主键。 */
    private Long slotId;

    /** 开始时刻。 */
    private LocalDateTime startInstant;

    /** 结束时刻。 */
    private LocalDateTime endInstant;

    /** 状态展示文案。 */
    private String statusView;
}
