package com.openhis.whale.booking.application.register.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 预约列表行 DTO。
 */
@Getter
@Setter
public class BookingListItemDto {

    /** 预约主键。 */
    private Long appointmentId;

    /** 业务单号。 */
    private String busNo;

    /** 患者主键。 */
    private Long patientId;

    /** 患者姓名（Port 填充）。 */
    private String patientName;

    /** 机构主键。 */
    private Long organizationId;

    /** 号源主键。 */
    private Long slotId;

    /** 计划开始。 */
    private LocalDateTime startInstant;

    /** 计划结束。 */
    private LocalDateTime endInstant;

    /** 去向诊室。 */
    private Long destinationLocationId;

    /** 展示状态。 */
    private String statusView;
}
