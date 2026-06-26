package com.openhis.whale.booking.application.template.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * TW 周排班模板 DTO（2.25）。
 */
@Getter
@Setter
public class WeekScheduleTemplateDto {

    /** 主键。 */
    private Long id;

    /** 科室主键。 */
    private Long organizationId;

    /** 模板编码。 */
    private String templateCode;

    /** 模板名称。 */
    private String templateName;

    /** 无 Schedule 时是否允许预约。 */
    private Boolean fallbackBookable;

    /** 科室默认周模板。 */
    private Boolean isDefault;

    /** 是否启用。 */
    private Boolean enabled;

    /** 生效起。 */
    private LocalDate effectiveStart;

    /** 生效止。 */
    private LocalDate effectiveEnd;

    /** 周模板明细列表。 */
    private List<WeekScheduleTemplateItemDto> items;
}
