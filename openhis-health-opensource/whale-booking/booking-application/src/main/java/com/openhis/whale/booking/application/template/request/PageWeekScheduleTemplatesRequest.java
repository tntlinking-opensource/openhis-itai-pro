package com.openhis.whale.booking.application.template.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * TW 周模板分页。
 */
@Getter
@Setter
public class PageWeekScheduleTemplatesRequest {

    /** 科室主键；空=全院。 */
    private Long organizationId;

    /** 模板编码/名称关键字。 */
    private String keyword;

    /** 页码（1-based）。 */
    @NotNull
    @Min(1)
    private Integer pageNo = 1;

    /** 页大小。 */
    @NotNull
    @Min(1)
    private Integer pageSize = 20;
}
