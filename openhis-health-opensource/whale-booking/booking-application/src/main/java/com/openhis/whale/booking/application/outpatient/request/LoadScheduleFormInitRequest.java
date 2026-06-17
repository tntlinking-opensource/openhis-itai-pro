package com.openhis.whale.booking.application.outpatient.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 排班台初始化请求（2.1）。
 */
@Getter
@Setter
public class LoadScheduleFormInitRequest {

    /** 科室主键；可空表示加载全院医师/诊室前不过滤科室。 */
    private Long organizationId;
}
