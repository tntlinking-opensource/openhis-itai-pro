package com.openhis.whale.booking.application.outpatient.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 停诊影响预览行。
 */
@Getter
@Setter
public class StopClinicImpactItemDto {

    /** 预约主键。 */
    private Long appointmentId;

    /** 业务单号。 */
    private String busNo;

    /** 患者主键。 */
    private Long patientId;

    /** 患者姓名。 */
    private String patientName;

    /** 源号源主键。 */
    private Long sourceSlotId;

    /** 源时段起。 */
    private LocalDateTime sourceStartTime;

    /** 源时段止。 */
    private LocalDateTime sourceEndTime;

    /** 目标号源主键。 */
    private Long targetSlotId;

    /** 目标时段起。 */
    private LocalDateTime targetStartTime;

    /** 目标时段止。 */
    private LocalDateTime targetEndTime;

    /** 处置状态：CAN_TRANSFER / MANUAL_REQUIRED / SKIP_REGISTERED / SKIP_CHECKED_IN。 */
    private String dispositionStatus;

    /** 说明文案。 */
    private String dispositionMessage;
}
