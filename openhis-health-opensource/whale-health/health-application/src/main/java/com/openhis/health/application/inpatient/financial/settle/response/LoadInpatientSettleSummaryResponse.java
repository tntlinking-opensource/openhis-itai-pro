/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
/** 住院结算摘要响应。 */
@Schema(description = "住院结算摘要响应")
public class LoadInpatientSettleSummaryResponse {

    @Schema(description = "就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 就诊 ID（业务字段）。 */
    private Long encounterId;

    /** 患者 ID。 */
    @Schema(description = "患者 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者 ID（业务字段）。 */
    private Long patientId;

    /** 住院号。 */
    @Schema(description = "住院号")
    /** 住院号（业务字段）。 */
    private String encounterBusNo;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    /** 患者姓名（业务字段）。 */
    private String patientName;

    /** 科室名称。 */
    @Schema(description = "科室名称")
    /** 科室名称（业务字段）。 */
    private String organizationName;

    /** 病区名称。 */
    @Schema(description = "病区名称")
    /** 病区名称（业务字段）。 */
    private String wardName;

    /** 床号。 */
    @Schema(description = "床号")
    /** 床号（业务字段）。 */
    private String bedName;

    /** 就诊状态 code。 */
    @Schema(description = "就诊状态 code")
    /** 就诊状态 code（业务字段）。 */
    private String statusEnum;

    /** 就诊状态展示。 */
    @Schema(description = "就诊状态展示")
    /** 就诊状态展示（业务字段）。 */
    private String statusEnumText;

    /** 全部待结算费用合计。 */
    @Schema(description = "全部待结算费用合计")
    /** 全部待结算费用合计（业务字段）。 */
    private BigDecimal pendingSettlementTotal;

    /** 预交金净值。 */
    @Schema(description = "预交金净值（非展示余额）")
    /** 预交金净值（非展示余额）（业务字段）。 */
    private BigDecimal prepayNetBalance;

    /** 本次选中费用合计。 */
    @Schema(description = "本次选中费用合计")
    /** 本次选中费用合计（业务字段）。 */
    private BigDecimal selectedTotal;

    /** 差额提示：预交金净值 − 已结算占用 − 本次选中合计。 */
    @Schema(description = "差额提示：预交金净值 − 已结算占用 − 本次选中合计")
    /** 差额提示：预交金净值 − 已结算占用 − 本次选中合计（业务字段）。 */
    private BigDecimal balanceHint;

    /** 展示余额：与可用余额一致（兼容）。 */
    @Schema(description = "展示余额：与可用余额一致")
    /** 展示余额：与可用余额一致（业务字段）。 */
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
