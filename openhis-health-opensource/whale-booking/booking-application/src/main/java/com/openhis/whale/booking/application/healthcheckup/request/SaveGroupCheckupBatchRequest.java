package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 保存团检批次请求（9.4）。
 */
@Getter
@Setter
public class SaveGroupCheckupBatchRequest {

    /** 团检批次主键（更新时传入）。 */
    private Long id;

    /** 团检单位主键。 */
    @NotNull
    private Long orgId;

    /** 批次名称。 */
    @NotBlank
    private String batchName;

    /** 体检套餐主键。 */
    private Long packageId;

    /** 日程主键。 */
    private Long scheduleId;

    /** 预约日期。 */
    @NotNull
    private LocalDate bookingDate;

    /** 开始时刻。 */
    private LocalTime startTime;

    /** 结束时刻。 */
    private LocalTime endTime;

    /** 容量上限。 */
    private Integer capacityLimit;

    /** 状态编码。 */
    private String statusCode;
}
