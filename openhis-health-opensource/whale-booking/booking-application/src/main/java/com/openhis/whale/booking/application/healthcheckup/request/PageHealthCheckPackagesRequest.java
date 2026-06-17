package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 体检套餐分页请求（9.1）。
 */
@Getter
@Setter
public class PageHealthCheckPackagesRequest {

    private Long organizationId;

    @NotNull
    private Integer pageNo;

    @NotNull
    private Integer pageSize;
}
