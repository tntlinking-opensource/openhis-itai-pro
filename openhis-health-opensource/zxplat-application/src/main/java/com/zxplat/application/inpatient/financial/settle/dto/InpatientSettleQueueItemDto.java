/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.settle.dto;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.shared.inpatient.InpatientAccountBalanceDisplayDtoApplier;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 住院结算队列卡片 DTO（IF-IP-STL-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院结算队列行")
public class InpatientSettleQueueItemDto
    implements InpatientAccountBalanceDisplayDtoApplier.AccountBalanceDisplayTarget {

    /** 住院就诊 ID（业务字段）。 */
    @Schema(description = "住院就诊 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者主键 {@code adm_patient.id}，须与就诊归属一致。 */
    @Schema(description = "患者 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 住院号（就诊业务编号），列表主展示列。 */
    @Schema(description = "住院号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String encounterBusNo;

    /** 患者姓名（读模型投影）。 */
    @Schema(description = "患者姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String patientName;

    /** 性别枚举 code（{@link com.zxplat.domain.share.enums.AdministrativeGender}）。 */
    @Schema(description = "性别 code")
    private String genderEnum;

    /** 性别中文展示。 */
    @Schema(description = "性别展示")
    private String genderEnumText;

    /** 年龄展示文案（由出生日期计算）。 */
    @Schema(description = "年龄展示")
    private String ageText;

    /** 住院科室名称。 */
    @Schema(description = "科室名称")
    private String organizationName;

    /** 病区名称。 */
    @Schema(description = "病区名称")
    private String wardName;

    /** 床号展示（病区-床位）。 */
    @Schema(description = "床号展示")
    private String bedName;

    /** 主治医生姓名（上下文展示口径：活动优先，清床/召回后末次 COMPLETED）。 */
    @Schema(description = "主治医生姓名")
    private String attendingDoctorName;

    /** 就诊状态 code（{@link com.zxplat.domain.share.enums.EncounterStatus}）。 */
    @Schema(description = "就诊状态 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusEnum;

    /** 就诊状态中文展示。 */
    @Schema(description = "就诊状态展示", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusEnumText;

    /** 入院时间。 */
    @Schema(description = "入院时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime startTime;

    /** 当前待结算费用合计（PENDING 收费项 total_price 汇总）。 */
    @Schema(description = "待结算费用合计")
    private BigDecimal pendingSettlementTotal;

    /** 最近一次成功结算时间（已结算页签展示）。 */
    @Schema(description = "最近结算时间（已结算页签）")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime lastSettleTime;

    /** 费用性质展示（合同名称/编号）。 */
    @Schema(description = "费用性质展示")
    private String contractDisplay;

    /** 登记账户关联合同 ID，医保读卡/预结算时使用。 */
    @Schema(description = "登记账户合同 ID（医保读卡用）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    /** 展示余额（与可用余额一致，兼容旧字段）。 */
    @Schema(description = "展示余额（与可用余额一致）")
    private BigDecimal accountBalance;

    /** 账户余额展示模式。 */
    @Schema(description = "账户余额展示模式")
    private String accountBalanceDisplayMode;

    /** 预交金净值。 */
    @Schema(description = "预交金净值")
    private BigDecimal prepayNetBalance;

    /** 可用余额。 */
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
