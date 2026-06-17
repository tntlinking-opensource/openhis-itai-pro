package com.openhis.whale.booking.application.outpatient.request;

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

    /** 生成后自动发布（默认仅草稿）。 */
    private Boolean autoPublish;

    /** 发布后按 T1/放号策略自动放号（须 autoPublish=true）。 */
    private Boolean autoProvision;
}
