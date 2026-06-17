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

    @NotNull
    private Long batchId;

    @NotNull
    private Integer pageNo;

    @NotNull
    private Integer pageSize;
}
