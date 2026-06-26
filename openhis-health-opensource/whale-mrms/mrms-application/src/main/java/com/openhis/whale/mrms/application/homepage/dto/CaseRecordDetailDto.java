/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.application.homepage.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 病案主档详情 DTO（首页页 loadPage 用）。
 */
@Getter
@Setter
public class CaseRecordDetailDto {

    /** 病案主档主键。 */
    private Long caseRecordId;

    /** 患者主键。 */
    private Long patientId;

    /** 就诊主键。 */
    private Long encounterId;

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
