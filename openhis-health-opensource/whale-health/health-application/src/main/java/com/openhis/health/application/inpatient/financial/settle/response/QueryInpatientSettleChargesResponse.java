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
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettleCategorySummaryDto;
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettleChargeLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 住院结算费用查询响应（IF-IP-STL-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院结算费用查询响应")
public class QueryInpatientSettleChargesResponse {

    @Schema(description = "就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    @Schema(description = "费用呈现模式 code")
    private String presentationMode;

    @Schema(description = "明细行列表（DETAIL 模式）")
    private List<InpatientSettleChargeLineDto> lines;

    @Schema(description = "大类汇总列表（SUMMARY 模式）")
    private List<InpatientSettleCategorySummaryDto> categories;

    @Schema(description = "当前条件下待结算合计")
    private BigDecimal pendingSettlementTotal;

    @Schema(description = "首次加载是否默认全选")
    private Boolean defaultSelectAll;
}
