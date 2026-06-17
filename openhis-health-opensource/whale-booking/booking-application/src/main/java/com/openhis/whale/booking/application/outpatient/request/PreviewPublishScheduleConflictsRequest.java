package com.openhis.whale.booking.application.outpatient.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 发布前冲突预览（BR-EXT-005/006）。
 */
@Getter
@Setter
public class PreviewPublishScheduleConflictsRequest {

    @NotNull
    private Long scheduleId;

    @NotNull
    private LocalDateTime planningHorizonStart;

    @NotNull
    private LocalDateTime planningHorizonEnd;
}
