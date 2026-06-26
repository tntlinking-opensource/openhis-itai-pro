package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 团检名单分页请求（9.5）。
 */
@Getter
@Setter
public class PageGroupCheckupRostersRequest {

    /** 团检批次主键。 */
    @NotNull
    private Long batchId;

    /** 页码（从 1 开始）。 */
    @NotNull
    private Integer pageNo;

    /** 每页条数。 */
    @NotNull
    private Integer pageSize;
}
