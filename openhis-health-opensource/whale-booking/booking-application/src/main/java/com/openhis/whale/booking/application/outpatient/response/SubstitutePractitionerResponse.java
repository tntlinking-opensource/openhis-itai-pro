package com.openhis.whale.booking.application.outpatient.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 替诊结果。
 */
@Getter
@Setter
public class SubstitutePractitionerResponse {

    private Long scheduleId;
    private Long originalPractitionerId;
    private Long substitutePractitionerId;
    private String substituteReason;
    private Boolean cleared;
}
