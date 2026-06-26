package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 团检批次分页请求（9.4）。
 */
@Getter
@Setter
public class PageGroupCheckupBatchesRequest {

    /** 团检单位主键。 */
    private Long orgId;

    /** 页码（从 1 开始）。 */
    @NotNull
    private Integer pageNo;

    /** 每页条数。 */
    @NotNull
    private Integer pageSize;
}
