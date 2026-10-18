/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.outpatient.request;

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

    /** 待发布日程主键。 */
    @NotNull
    private Long scheduleId;

    /** 拟绑定规划窗口起。 */
    @NotNull
    private LocalDateTime planningHorizonStart;

    /** 拟绑定规划窗口止。 */
    @NotNull
    private LocalDateTime planningHorizonEnd;
}
