package com.openhis.whale.booking.application.outpatient.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 释放停诊预览冻结请求（TC-FRZ-01）。
 */
@Getter
@Setter
public class ReleaseStopClinicPreviewFreezeRequest {

    @NotNull
    private Long organizationId;

    @NotNull
    private Long scheduleId;

    /** 部分停诊号源；空表示释放该日程下全部预览冻结。 */
    private List<Long> slotIds;
}
