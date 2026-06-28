package com.openhis.whale.booking.contracts.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 住院床位预约快照（health 分床联动读 Port）。
 */
@Getter
@Setter
public class InpatientBedReservationSnapshot {

    /** 预约主键。 */
    private Long appointmentId;

    /** 床位 Location 主键。 */
    private Long bedLocationId;

    /** 患者主键。 */
    private Long patientId;

    /** 拟入院日期。 */
    private LocalDate intendedAdmissionDate;
}
