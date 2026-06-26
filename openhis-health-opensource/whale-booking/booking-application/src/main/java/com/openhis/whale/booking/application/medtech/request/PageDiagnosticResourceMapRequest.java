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

    /** 机构/科室主键（可选筛选）。 */
    private Long organizationId;

    /** 页码（1-based）。 */
    @NotNull
    private Integer pageNo;

    /** 每页条数。 */
    @NotNull
    private Integer pageSize;
}
