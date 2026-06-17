package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
public class SaveGroupBatchRequest {

    private Long id;
    private Long groupOrgId;
    private String groupOrgName;

    @NotBlank
    private String batchName;

    private LocalDate bookingDate;
    private LocalTime slotStartTime;
    private Integer capacity;
    private Integer bookedCount;
    private String packageName;
    private Long packageId;
    private Long scheduleId;
}
