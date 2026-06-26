package com.openhis.whale.booking.application.template.response;

import lombok.Getter;
import lombok.Setter;

/**
 * TW 周模板明细 DTO。
 */
@Getter
@Setter
public class WeekScheduleTemplateItemDto {

    /** 明细主键。 */
    private Long id;

    /** 1=周一 … 7=周日。 */
    private Integer weekday;

    /** 关联 T2 日模板。 */
    private Long dayScheduleTemplateId;

    /** T2 日模板名称。 */
    private String dayScheduleTemplateName;

    /** 同日多条排序。 */
    private Integer sortNo;

    /** 是否启用。 */
    private Boolean enabled;
}
