/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.outpatient.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 编辑草稿日程请求（2.6）。
 */
@Getter
@Setter
public class UpdateScheduleDraftRequest {

    /** 日程主键。 */
    @NotNull
    private Long scheduleId;

    /** 参与者全量替换（可选）。 */
    @Valid
    private List<ScheduleActorRequest> actors;

    /** 日程显示名称（可选）。 */
    private String name;

    /** 规划窗口起（可选）。 */
    private LocalDateTime planningHorizonStart;

    /** 规划窗口止（可选）。 */
    private LocalDateTime planningHorizonEnd;

    /** 服务口径（可选）。 */
    @Valid
    private ScheduleServiceProfileRequest serviceProfile;

    /** 说明 Markdown（可选）。 */
    private String commentMarkdown;

    /** 关联 T1 放号时段模板主键（可选）。 */
    private Long slotProvisionTemplateId;
}
