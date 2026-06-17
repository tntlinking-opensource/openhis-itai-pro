package com.openhis.whale.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GroupRosterDto {

    private Long id;
    private Long batchId;
    private String name;
    private String idCardNoMasked;
    private String studentNo;
    private String phoneMasked;
    private String bookingStatusView;
    private String appointmentBusNo;
}
