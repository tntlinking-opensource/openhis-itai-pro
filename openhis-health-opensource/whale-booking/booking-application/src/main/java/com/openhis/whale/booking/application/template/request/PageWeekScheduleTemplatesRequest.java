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

    private Long organizationId;

    private String keyword;

    @NotNull
    @Min(1)
    private Integer pageNo = 1;

    @NotNull
    @Min(1)
    private Integer pageSize = 20;
}
