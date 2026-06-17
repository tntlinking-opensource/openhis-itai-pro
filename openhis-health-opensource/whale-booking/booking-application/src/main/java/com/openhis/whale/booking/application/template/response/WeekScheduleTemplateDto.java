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

    private Long id;

    private Long organizationId;

    private String templateCode;

    private String templateName;

    /** 无 Schedule 时是否允许预约。 */
    private Boolean fallbackBookable;

    /** 科室默认周模板。 */
    private Boolean isDefault;

    private Boolean enabled;

    private LocalDate effectiveStart;

    private LocalDate effectiveEnd;

    private List<WeekScheduleTemplateItemDto> items;
}
