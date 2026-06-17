package com.openhis.whale.booking.application.slot.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 号源日历分页查询请求（3.1）。
 */
@Getter
@Setter
public class PageSlotsByDateRequest {

    /** 科室/机构主键。 */
    @NotNull
    private Long organizationId;

    /** 查询日期。 */
    @NotNull
    private LocalDate date;

    /** 页码（1-based）。 */
    @NotNull
    private Integer pageNo;

    /** 页大小。 */
    @NotNull
    private Integer pageSize;

    /** 日程主键（可选）。 */
    private Long scheduleId;

    /** 医师主键（可选，经 Schedule 参与者收窄）。 */
    private Long practitionerId;

    /** 状态码（可选）。 */
    private String statusCode;
}
