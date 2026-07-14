/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.ATDManager.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 床位列表 — 占床患者块（对齐 001 列表卡片核心展示，BR-IP-ATD-001）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "占床患者块")
public class BedBoardOccupantDto {

    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    @Schema(description = "患者主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 性别 code。 */
    @Schema(description = "性别 code")
    private String genderEnum;

    /** 性别展示文案。 */
    @Schema(description = "性别展示文案")
    private String genderEnumText;

    /** 年龄文本。 */
    @Schema(description = "年龄文本")
    private String ageText;

    /** 住院号。 */
    @Schema(description = "住院号")
    private String encounterBusNo;

    /** 就诊状态 code。 */
    @Schema(description = "就诊状态 code")
    private String statusEnum;

    /** 就诊状态展示文案。 */
    @Schema(description = "就诊状态展示文案")
    private String statusEnumText;

    /** 病情 code。 */
    @Schema(description = "病情 code")
    private String priorityEnum;

    /** 护理级别展示名。 */
    @Schema(description = "护理级别展示名")
    private String nursingLevelName;

    /** 入科时间。 */
    @Schema(description = "入科时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime admissionTime;

    /** 住院医师姓名摘要。 */
    @Schema(description = "住院医师姓名摘要")
    private String attendingDoctorName;

    /** 责任护士姓名摘要。 */
    @Schema(description = "责任护士姓名摘要")
    private String primaryNurseName;

    /** 诊断一行摘要。 */
    @Schema(description = "诊断一行摘要")
    private String diagnosisSummary;

    /** 展示用账户余额。 */
    @Schema(description = "展示用账户余额")
    private BigDecimal accountBalance;

    /** 低余额预警。 */
    @Schema(description = "低余额预警")
    private Boolean lowBalanceWarning;
}
