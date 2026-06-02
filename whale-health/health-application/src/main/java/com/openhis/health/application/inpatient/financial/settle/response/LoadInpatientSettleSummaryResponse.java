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
@Schema(description = "住院结算摘要响应")
public class LoadInpatientSettleSummaryResponse {

    @Schema(description = "就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    @Schema(description = "患者 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    @Schema(description = "住院号")
    private String encounterBusNo;

    @Schema(description = "患者姓名")
    private String patientName;

    @Schema(description = "科室名称")
    private String organizationName;

    @Schema(description = "病区名称")
    private String wardName;

    @Schema(description = "床号")
    private String bedName;

    @Schema(description = "就诊状态 code")
    private String statusEnum;

    @Schema(description = "就诊状态展示")
    private String statusEnumText;

    @Schema(description = "全部待结算费用合计")
    private BigDecimal pendingSettlementTotal;

    @Schema(description = "预交金净值（非展示余额）")
    private BigDecimal prepayNetBalance;

    @Schema(description = "本次选中费用合计")
    private BigDecimal selectedTotal;

    @Schema(description = "差额提示：预交金净值 − 已结算占用 − 本次选中合计")
    private BigDecimal balanceHint;

    @Schema(description = "展示余额：预交金净值 −（待结算 + 已结算占用）")
    private BigDecimal displayAccountBalance;
}
