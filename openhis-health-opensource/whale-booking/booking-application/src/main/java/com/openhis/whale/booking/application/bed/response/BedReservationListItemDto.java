package com.openhis.whale.booking.application.bed.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 床位预留列表行 DTO。
 */
@Getter
@Setter
public class BedReservationListItemDto {

    /** 预约主键。 */
    private Long appointmentId;

    /** 预约业务流水号。 */
    private String busNo;

    /** 患者主键。 */
    private Long patientId;

    /** 患者姓名。 */
    private String patientName;

    /** 床位地点主键。 */
    private Long bedLocationId;

    /** 床位名称。 */
    private String bedName;

    /** 拟入院日期。 */
    private String intendedAdmissionDate;

    /** 状态展示文案。 */
    private String statusView;
}
