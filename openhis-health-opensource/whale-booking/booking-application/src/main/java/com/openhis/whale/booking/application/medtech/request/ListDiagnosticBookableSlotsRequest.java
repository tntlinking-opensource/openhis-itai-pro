package com.openhis.whale.booking.application.medtech.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 医技可约号源查询请求。
 */
@Getter
@Setter
public class ListDiagnosticBookableSlotsRequest {

    /** 机构/科室主键。 */
    @NotNull
    private Long organizationId;

    /** 医技医嘱主键。 */
    @NotNull
    private Long serviceRequestId;

    /** 查询日期。 */
    @NotNull
    private LocalDate date;
}
