package com.openhis.whale.booking.application.outpatient.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 恢复开诊响应。
 */
@Getter
@Setter
public class ReactivateScheduleResponse {

    private Long scheduleId;

    /** 提示：恢复后须手动重新放号。 */
    private Boolean needReprovision;
}
