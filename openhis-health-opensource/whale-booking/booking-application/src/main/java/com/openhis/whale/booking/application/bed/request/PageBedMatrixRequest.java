package com.openhis.whale.booking.application.bed.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 床位矩阵查询请求。
 */
@Getter
@Setter
public class PageBedMatrixRequest {

    @NotNull
    private Long organizationId;

    @NotNull
    private Long wardLocationId;

    @NotNull
    private LocalDate rangeStart;

    @NotNull
    private LocalDate rangeEnd;
}
