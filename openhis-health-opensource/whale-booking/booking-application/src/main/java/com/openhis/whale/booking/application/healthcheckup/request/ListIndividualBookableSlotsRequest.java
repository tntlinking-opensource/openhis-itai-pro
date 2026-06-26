package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 查询个检可约号源请求（6.x 旧版 API 别名）。
 */
@Getter
@Setter
public class ListIndividualBookableSlotsRequest {

    /** 机构/科室主键。 */
    @NotNull
    private Long organizationId;

    /** 体检套餐主键。 */
    @NotNull
    private Long packageId;

    /** 查询日期。 */
    @NotNull
    private LocalDate date;
}
