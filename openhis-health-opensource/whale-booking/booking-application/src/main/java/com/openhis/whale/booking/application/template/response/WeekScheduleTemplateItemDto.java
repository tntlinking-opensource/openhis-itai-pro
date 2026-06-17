package com.openhis.whale.booking.application.template.response;

import lombok.Getter;
import lombok.Setter;

/**
 * TW 周模板明细 DTO。
 */
@Getter
@Setter
public class WeekScheduleTemplateItemDto {

    private Long id;

    /** 1=周一 … 7=周日。 */
    private Integer weekday;

    /** 关联 T2 日模板。 */
    private Long dayScheduleTemplateId;

    private String dayScheduleTemplateName;

    private Integer sortNo;

    private Boolean enabled;
}
