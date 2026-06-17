package com.openhis.whale.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
public class GroupCheckupBatchDto {

    private Long id;

    private Long orgId;

    private String orgName;

    private String batchName;

    private Long packageId;

    private Long scheduleId;

    private LocalDate bookingDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private Integer capacityLimit;

    private Integer bookedCount;

    private String statusCode;
}
