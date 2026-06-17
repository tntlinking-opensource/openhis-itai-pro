package com.openhis.whale.booking.application.outpatient.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class DepartmentPractitionerGenerateItemDto {

    private Long practitionerId;
    private String practitionerName;
    private List<Long> scheduleIds = new ArrayList<>();
    private String message;
}
