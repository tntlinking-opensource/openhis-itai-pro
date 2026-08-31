/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.settle.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 住院结算摘要响应（IF-IP-STL-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院结算摘要响应")
public class LoadInpatientSettleSummaryResponse {

    /** 就诊 ID（业务字段）。 */
    @Schema(description = "就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者 ID。 */
    @Schema(description = "患者 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 住院号。 */
    @Schema(description = "住院号")
    private String encounterBusNo;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 科室名称。 */
    @Schema(description = "科室名称")
    private String organizationName;

    /** 病区名称。 */
    @Schema(description = "病区名称")
    private String wardName;

    /** 床号。 */
    @Schema(description = "床号")
    private String bedName;

    /** 主治医生姓名（上下文展示口径：活动优先，清床/召回后末次 COMPLETED）。 */
    @Schema(description = "主治医生姓名")
    private String attendingDoctorName;

    /** 就诊状态 code。 */
    @Schema(description = "就诊状态 code")
    private String statusEnum;

    /** 就诊状态展示。 */
    @Schema(description = "就诊状态展示")
    private String statusEnumText;

    /** 全部待结算费用合计。 */
    @Schema(description = "全部待结算费用合计")
    private BigDecimal pendingSettlementTotal;

    /** 预交金净值。 */
    @Schema(description = "预交金净值（非展示余额）")
    private BigDecimal prepayNetBalance;

    /** 本次选中费用合计。 */
    @Schema(description = "本次选中费用合计")
    private BigDecimal selectedTotal;

    /**
     * 差额提示：库内余额 − 选中合计（调试保留；UI 主路径勿当应付）。
     *
     * <p>个人现金应付见 {@link #personalCashPayable}；渠道实付见 {@link #channelPayable} /
     * 预结算 {@code displayAmount}。</p>
     */
    @Schema(description = "差额提示（调试；勿当应付）")
    private BigDecimal balanceHint;

    /**
     * 个人现金应付 P 预估（自费≈选中合计；医保以预结算为准）。
     */
    @Schema(description = "个人现金应付 P 预估")
    private BigDecimal personalCashPayable;

    /** 预交金冲抵额 O 预估。 */
    @Schema(description = "预交金冲抵额 O 预估")
    private BigDecimal depositOffsetAmount;

    /** 渠道实付 C 预估（收银应付）。 */
    @Schema(description = "渠道实付 C 预估")
    private BigDecimal channelPayable;

    /** 应退预交金预估。 */
    @Schema(description = "应退预交金预估")
    private BigDecimal prepayRefundAmount;

    /** 是否全额退预交金预估。 */
    @Schema(description = "是否全额退预交金预估")
    private Boolean prepayFullRefund;

    /** 轧差分支码 R1～R7。 */
    @Schema(description = "预交金轧差分支 R1～R7")
    private String depositSettleRuleCode;

    /** 展示余额：与可用余额一致（兼容）。 */
    @Schema(description = "展示余额：与可用余额一致")
    private BigDecimal displayAccountBalance;

    /** 账户余额展示模式。 */
    @Schema(description = "账户余额展示模式")
    private String accountBalanceDisplayMode;

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
