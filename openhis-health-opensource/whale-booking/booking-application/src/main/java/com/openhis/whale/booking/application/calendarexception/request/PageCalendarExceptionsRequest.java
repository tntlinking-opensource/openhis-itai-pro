package com.openhis.whale.booking.application.calendarexception.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * T3 日历例外分页请求。
 */
@Getter
@Setter
public class PageCalendarExceptionsRequest {

    /** 科室主键；空=全院。 */
    private Long organizationId;

    /** 例外类型筛选。 */
    private String exceptionType;

    /** 日期区间起。 */
    private LocalDate rangeStart;

    /** 日期区间止。 */
    private LocalDate rangeEnd;

    /** 说明关键字。 */
    private String keyword;

    /** 页码（1-based）。 */
    @NotNull
    private Integer pageNo;

    /** 页大小。 */
    @NotNull
    private Integer pageSize;
}
