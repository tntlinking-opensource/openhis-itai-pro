package com.openhis.whale.booking.application.outpatient.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 发布日程请求（2.7）。
 */
@Getter
@Setter
public class PublishScheduleRequest {

    /** 日程主键。 */
    @NotNull
    private Long scheduleId;

    /** 规划窗口起。 */
    @NotNull
    private LocalDateTime planningHorizonStart;

    /** 规划窗口止。 */
    @NotNull
    private LocalDateTime planningHorizonEnd;

    /** 已确认发布冲突警告（BR-EXT-005/006）。 */
    private Boolean acknowledgeConflictWarnings;
}
