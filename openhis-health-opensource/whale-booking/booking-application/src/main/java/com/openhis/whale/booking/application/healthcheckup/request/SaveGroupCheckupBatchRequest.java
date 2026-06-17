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

    private Long id;

    @NotNull
    private Long orgId;

    @NotBlank
    private String batchName;

    private Long packageId;

    private Long scheduleId;

    @NotNull
    private LocalDate bookingDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private Integer capacityLimit;

    private String statusCode;
}
