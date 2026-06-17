package com.openhis.whale.booking.application.outpatient.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 周历/月历查询请求（2.2）。
 */
@Getter
@Setter
public class PageSchedulesByCalendarRequest {

    /** 科室/机构主键。 */
    @NotNull
    private Long organizationId;

    /** 可见区间起（含）。 */
    @NotNull
    private LocalDate rangeStart;

    /** 可见区间止（含）。 */
    @NotNull
    private LocalDate rangeEnd;

    /** 医师主键（可选）。 */
    private Long practitionerId;

    /** 诊室主键（可选）。 */
    private Long locationId;

    /** 号源类型主键（可选）。 */
    private Long healthcareServiceId;
}
