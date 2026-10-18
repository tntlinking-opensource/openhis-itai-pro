/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.execution.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.shared.inpatient.InpatientAccountBalanceDisplayDtoApplier;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 医技工作站患者队列行（AS-MT-EXE-001 IF-MT-EXE-01）。
 *
 * <p>住院行含账户余额展示字段，口径对齐护士站队列（库内余额 − 待结算）。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医技工作站患者队列行")
public class MedicalTechPatientQueueItemDto
    implements InpatientAccountBalanceDisplayDtoApplier.AccountBalanceDisplayTarget {

    /** 就诊主键 {@code adm_encounter.id}。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键")
    private Long encounterId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 性别持久化码。 */
    @Schema(description = "性别持久化码")
    private String genderEnum;

    /** 性别展示文案。 */
    @Schema(description = "性别展示文案")
    private String genderEnumText;

    /** 出生日期。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    @Schema(description = "出生日期")
    private LocalDate birthDate;

    /** 年龄展示串。 */
    @Schema(description = "年龄展示")
    private String ageText;

    /** 业务号/流水号。 */
    @Schema(description = "业务号")
    private String slipNo;

    /**
     * 就诊开始时间。
     *
     * <p>门诊=挂号/就诊开始；住院=入院时间（卡片展示「入院时间」）。</p>
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "就诊开始/入院时间")
    private LocalDateTime encounterTime;

    /**
     * 出院时间（仅住院；{@code adm_encounter.end_time}）。
     *
     * <p>在院未出院时常为空；卡片与门诊「就诊时间」分列展示。</p>
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "出院时间（住院）")
    private LocalDateTime dischargeTime;

    /** 费用性质展示名。 */
    @Schema(description = "费用性质展示")
    private String contractDisplay;

    /** 患者手机号（门诊/住院信息条回显）。 */
    @Schema(description = "手机号")
    private String phone;

    /** 患者身份证号（门诊/住院信息条回显）。 */
    @Schema(description = "身份证号")
    private String idCard;

    /** 本登录 TECH 科是否仍有待发放耗材。 */
    @Schema(description = "本站是否有待发耗材")
    private Boolean hasPendingDevice;

    /** 就诊状态持久化码（仅住院）。 */
    @Schema(description = "就诊状态码（住院）")
    private String statusEnum;

    /** 就诊状态展示文案（仅住院）。 */
    @Schema(description = "就诊状态展示（住院）")
    private String statusEnumText;

    /** 护理级别展示名（仅住院）。 */
    @Schema(description = "护理级别（住院）")
    private String nursingLevelName;

    /** 病区名称（仅住院）。 */
    @Schema(description = "病区名称（住院）")
    private String wardName;

    /** 病房名称（仅住院；可空）。 */
    @Schema(description = "病房名称（住院）")
    private String houseName;

    /** 床位名称（仅住院）。 */
    @Schema(description = "床位名称（住院）")
    private String bedName;

    /** 诊断摘要（仅住院）。 */
    @Schema(description = "诊断摘要（住院）")
    private String diagnosisSummary;

    /** 展示用账户余额（与可用余额一致；仅住院有值）。 */
    @Schema(description = "展示用账户余额")
    private BigDecimal accountBalance;

    /** 账户余额展示模式。 */
    @Schema(description = "账户余额展示模式")
    private String accountBalanceDisplayMode;

    /** 预交金净值。 */
    @Schema(description = "预交金净值")
    private BigDecimal prepayNetBalance;

    /** 待结算费用合计。 */
    @Schema(description = "待结算费用合计")
    private BigDecimal pendingSettlementTotal;

    /** 可用余额（库内余额 − 待结算）。 */
    @Schema(description = "可用余额")
    private BigDecimal availableBalance;

    /** 账户余额主展示文案。 */
    @Schema(description = "账户余额主展示文案")
    private String accountBalanceSummaryText;

    /** 账户余额副展示文案。 */
    @Schema(description = "账户余额副展示文案")
    private String accountBalanceSubText;

    /** 低余额预警。 */
    @Schema(description = "低余额预警")
    private Boolean lowBalanceWarning;
}
