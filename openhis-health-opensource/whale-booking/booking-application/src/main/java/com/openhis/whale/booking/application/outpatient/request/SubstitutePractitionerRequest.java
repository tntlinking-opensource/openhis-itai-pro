package com.openhis.whale.booking.application.outpatient.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 替诊请求（2.21 · CHG-SCH-08）。
 */
@Getter
@Setter
public class SubstitutePractitionerRequest {

    /** 待替诊日程主键。 */
    @NotNull
    private Long scheduleId;

    /** 替诊医师；空=撤销替诊。 */
    private Long substitutePractitionerId;

    /** 替诊原因。 */
    private String reason;
}
