package com.openhis.whale.booking.application.template.request;

import lombok.Getter;
import lombok.Setter;

/**
 * TW 周模板明细保存项。
 */
@Getter
@Setter
public class SaveWeekScheduleTemplateItemRequest {

    /** 1=周一 … 7=周日。 */
    private Integer weekday;

    /** 关联 T2 日模板。 */
    private Long dayScheduleTemplateId;

    /** 同日多条排序。 */
    private Integer sortNo;

    /** 是否启用。 */
    private Boolean enabled;
}
