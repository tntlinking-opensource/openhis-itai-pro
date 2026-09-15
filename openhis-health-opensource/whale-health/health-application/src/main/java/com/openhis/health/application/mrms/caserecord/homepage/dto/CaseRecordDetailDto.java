/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.caserecord.homepage.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 病案主档详情 DTO（首页页 loadPage 用）。
 *
 * <p>Long 主键出参统一字符串，供前端诊断医生下拉与同科室门禁比对 {@code organizationId}。</p>
 */
@Getter
@Setter
public class CaseRecordDetailDto {

    /** 病案主档主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long caseRecordId;

    /** 患者主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /**
     * 入院科室主键（就诊 {@code organization_id}）。
     * <p>前端出院诊断「诊断医生」下拉按此科室装载出诊医师；不落首页表。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;

    /** 院内病案号。 */
    private String medcasno;

    /** 病案状态码。 */
    private String statusCode;

    /** 病案状态展示名。 */
    private String statusLabel;

    /** 本院第几次住院。 */
    private Integer patnIptCnt;

    /** 退回原因。 */
    private String returnReason;

    /** 归档时间。 */
    private LocalDateTime archivedAt;

    /** 病案质量等级。 */
    private String qualityGradeCode;
}
