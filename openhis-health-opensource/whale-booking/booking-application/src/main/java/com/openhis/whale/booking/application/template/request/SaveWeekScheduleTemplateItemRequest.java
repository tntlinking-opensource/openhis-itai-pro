package com.openhis.whale.booking.application.template.request;

import lombok.Getter;
import lombok.Setter;

/**
 * TW 周模板明细保存项。
 */
@Getter
@Setter
public class SaveWeekScheduleTemplateItemRequest {

    private Integer weekday;

    private Long dayScheduleTemplateId;

    private Integer sortNo;

    private Boolean enabled;
}
