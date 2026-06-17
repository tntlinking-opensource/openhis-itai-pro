package com.openhis.whale.booking.application.bed.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 床位预留列表行 DTO。
 */
@Getter
@Setter
public class BedReservationListItemDto {

    private Long appointmentId;
    private String busNo;
    private Long patientId;
    private String patientName;
    private Long bedLocationId;
    private String bedName;
    private String intendedAdmissionDate;
    private String statusView;
}
