package com.openhis.whale.booking.application.outpatient.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CopyScheduleToNextWeekRequest {

    @NotNull
    private Long scheduleId;
}
