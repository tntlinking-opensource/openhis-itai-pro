/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.prepay.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.inpatient.InpatientAccountBalanceDisplayDtoApplier;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预交金在院患者队列卡片 DTO（IF-IP-PREP-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "预交金在院患者队列行")
public class PrepayQueueItemDto implements InpatientAccountBalanceDisplayDtoApplier.AccountBalanceDisplayTarget {

    /** 住院就诊 ID。 */
    @Schema(description = "住院就诊 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者 ID。 */
    @Schema(description = "患者 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 住院号。 */
    @Schema(description = "住院号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String encounterBusNo;

    /** 患者姓名。 */
    @Schema(description = "患者姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String patientName;

    /** 性别 code。 */
    @Schema(description = "性别 code")
    private String genderEnum;

    /** 性别展示。 */
    @Schema(description = "性别展示")
    private String genderEnumText;

    /** 年龄展示。 */
    @Schema(description = "年龄展示")
    private String ageText;

    /** 科室名称。 */
    @Schema(description = "科室名称")
    private String organizationName;

    /** 病区名称。 */
    @Schema(description = "病区名称")
    private String wardName;

    /** 床号展示。 */
    @Schema(description = "床号展示")
    private String bedName;

    /** 就诊状态 code。 */
    @Schema(description = "就诊状态 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusEnum;

    /** 就诊状态展示。 */
    @Schema(description = "就诊状态展示", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusEnumText;

    /** 入院时间。 */
    @Schema(description = "入院时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime startTime;

    /** 账户余额展示模式（NORMAL / REVERSED_PENDING_RESETTLE）。 */
    @Schema(description = "账户余额展示模式")
    private String accountBalanceDisplayMode;

    /** 库内账户余额（{@code adm_account.balance}）。 */
    @Schema(description = "库内账户余额")
    private BigDecimal prepayNetBalance;

    /** 待结算费用合计。 */
    @Schema(description = "待结算费用合计")
    private BigDecimal pendingSettlementTotal;

    /** 可用余额（展示）：库内余额 − 待结算。 */
    @Schema(description = "可用余额（展示）")
    private BigDecimal availableBalance;

    /** 兼容字段：与可用余额一致。 */
    @Schema(description = "账户余额（展示，与可用余额一致）", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal accountBalance;

    /** 账户余额主展示文案。 */
    @Schema(description = "账户余额主展示文案")
    private String accountBalanceSummaryText;

    /** 账户余额副展示文案。 */
    @Schema(description = "账户余额副展示文案")
    private String accountBalanceSubText;

    /** 低余额预警。 */
    @Schema(description = "低余额预警")
    private Boolean lowBalanceWarning;

    /** 费用性质/医保类型展示。 */
    @Schema(description = "费用性质/医保类型展示")
    private String contractDisplay;
}
