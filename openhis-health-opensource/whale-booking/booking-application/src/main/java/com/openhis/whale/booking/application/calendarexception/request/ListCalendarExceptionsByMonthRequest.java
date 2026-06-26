package com.openhis.whale.booking.application.calendarexception.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 月历标记查询（T3 Tab 左侧）。
 */
@Getter
@Setter
public class ListCalendarExceptionsByMonthRequest {

    /** 科室主键；空=全院。 */
    private Long organizationId;

    /** 格式 yyyy-MM。 */
    @NotBlank
    private String month;
}
