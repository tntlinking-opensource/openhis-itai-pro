package com.openhis.whale.booking.application.calendarexception.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * T3 日历例外 DTO。
 */
@Getter
@Setter
public class CalendarExceptionDto {

    /** 主键。 */
    private Long id;

    /** 科室主键；空=全院。 */
    private Long organizationId;

    /** 例外日期或区间起点。 */
    private LocalDate exceptionDate;

    /** 区间终点；空=单日。 */
    private LocalDate rangeEnd;

    /** 例外类型：CLOSED / REDUCED / OVERRIDE / EXTRA。 */
    private String exceptionType;

    /** REDUCED/OVERRIDE/EXTRA 时关联 T2 日模板。 */
    private Long overrideScheduleTemplateId;

    /** 说明（国庆、调休等）。 */
    private String reason;

    /** 是否参与从模板生成。 */
    private Boolean autoApplyOnGenerate;
}
