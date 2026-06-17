package com.openhis.whale.booking.application.outpatient.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PreviewPublishScheduleConflictsResponse {

    private Boolean hasConflict;
    private List<SchedulePublishConflictDto> conflicts = new ArrayList<>();
}
