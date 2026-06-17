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

    @NotNull
    private Long organizationId;

    private Long encounterId;

    @NotNull
    private Integer pageNo;

    @NotNull
    private Integer pageSize;
}
