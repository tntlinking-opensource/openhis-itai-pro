package com.openhis.whale.booking.application.bed.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 床位预留列表分页请求。
 */
@Getter
@Setter
public class PageBedReservationsRequest {

    @NotNull
    private Long organizationId;

    private Long wardLocationId;

    private LocalDate dateFrom;

    private LocalDate dateTo;

    @NotNull
    private Integer pageNo;

    @NotNull
    private Integer pageSize;
}
