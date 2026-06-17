package com.openhis.whale.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GroupCheckupRosterDto {

    private Long id;

    private Long batchId;

    private Long patientId;

    private String fullName;

    private String idCardNo;

    private String studentNo;

    private String mobilePhone;

    private Long appointmentId;

    private String bookingStatusCode;
}
