package com.openhis.whale.booking.application.outpatient.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SchedulePublishConflictDto {

    private String conflictType;
    private Long resourceId;
    private Long conflictScheduleId;
    private String conflictScheduleName;
    private String message;
}
