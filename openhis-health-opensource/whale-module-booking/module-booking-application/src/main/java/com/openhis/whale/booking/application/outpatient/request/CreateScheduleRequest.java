/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.outpatient.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建门诊日程请求（2.3）。
 */
@Getter
@Setter
public class CreateScheduleRequest {

    /** 科室/机构主键。 */
    @NotNull
    private Long organizationId;

    /** 日程显示名称。 */
    private String name;

    /** 参与者列表（至少一条）。 */
    @NotEmpty
    @Valid
    private List<ScheduleActorRequest> actors;

    /** 规划窗口起（草稿创建时可选，绑定后才能在周历/月历占格）。 */
    private LocalDateTime planningHorizonStart;

    /** 规划窗口止。 */
    private LocalDateTime planningHorizonEnd;

    /** 关联 T1 放号时段模板主键。 */
    private Long slotProvisionTemplateId;
}
