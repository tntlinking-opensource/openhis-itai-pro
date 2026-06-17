package com.openhis.whale.booking.application.medtech.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 医技资源映射分页请求（7.7）。
 */
@Getter
@Setter
public class PageDiagnosticResourceMapRequest {

    private Long organizationId;

    @NotNull
    private Integer pageNo;

    @NotNull
    private Integer pageSize;
}
