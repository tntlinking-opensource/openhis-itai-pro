package com.openhis.whale.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
public class GroupBatchDto {

    private Long id;
    private Long groupOrgId;
    private String groupOrgName;
    private String batchName;
    private LocalDate bookingDate;
    private LocalTime slotStartTime;
    private Integer capacity;
    private Integer bookedCount;
    private String packageName;
}
