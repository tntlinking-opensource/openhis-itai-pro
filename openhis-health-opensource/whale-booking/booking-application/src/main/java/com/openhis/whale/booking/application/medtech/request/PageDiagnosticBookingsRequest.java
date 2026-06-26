package com.openhis.whale.booking.application.medtech.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 医技预约列表分页请求。
 */
@Getter
@Setter
public class PageDiagnosticBookingsRequest {

    /** 机构/科室主键。 */
    @NotNull
    private Long organizationId;

    /** 就诊主键（可选筛选）。 */
    private Long encounterId;

    /** 页码（1-based）。 */
    @NotNull
    private Integer pageNo;

    /** 每页条数。 */
    @NotNull
    private Integer pageSize;
}
