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

    private Long orgId;

    @NotNull
    private Integer pageNo;

    @NotNull
    private Integer pageSize;
}
