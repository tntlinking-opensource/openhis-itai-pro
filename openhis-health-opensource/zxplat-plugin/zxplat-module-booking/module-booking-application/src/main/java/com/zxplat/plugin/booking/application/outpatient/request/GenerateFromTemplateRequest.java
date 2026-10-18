/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.outpatient.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 从模板生成 Schedule（2.17 · 2.25 支持 DAY/WEEK）。
 */
@Getter
@Setter
public class GenerateFromTemplateRequest {

    /** 科室主键。 */
    @NotNull
    private Long organizationId;

    /** 模板粒度：DAY（默认）| WEEK。 */
    private String templateKind;

    /** T2 日模板主键（templateKind=DAY）。 */
    private Long scheduleTemplateId;

    /** TW 周模板主键（templateKind=WEEK）。 */
    private Long weekScheduleTemplateId;

    /** 生成区间起（含）。 */
    @NotNull
    private LocalDate rangeStart;

    /** 生成区间止（含）。 */
    @NotNull
    private LocalDate rangeEnd;

    /** 生成后是否自动发布为 active（默认 false；前端推荐开启）。 */
    private Boolean autoPublish;

    /** 发布后是否按 T1/放号策略自动放号（须 {@link #autoPublish} 为 true）。 */
    private Boolean autoProvision;
}
