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
import com.openhis.health.module.querysupport.financial.dto.InpatientSettleCategorySummaryDto;
import com.openhis.health.module.querysupport.financial.dto.InpatientSettleChargeLineDto;
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
/** 住院结算费用查询响应。 */
@Schema(description = "住院结算费用查询响应")
public class QueryInpatientSettleChargesResponse {

    @Schema(description = "就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 就诊 ID（业务字段）。 */
    private Long encounterId;

    /** 费用呈现模式 code。 */
    @Schema(description = "费用呈现模式 code")
    /** 费用呈现模式 code（业务字段）。 */
    private String presentationMode;

    /** 明细行列表。 */
    @Schema(description = "明细行列表（DETAIL 模式）")
    /** 明细行列表（DETAIL 模式）（业务字段）。 */
    private List<InpatientSettleChargeLineDto> lines;

    /** 大类汇总列表。 */
    @Schema(description = "大类汇总列表（SUMMARY 模式）")
    /** 大类汇总列表（SUMMARY 模式）（业务字段）。 */
    private List<InpatientSettleCategorySummaryDto> categories;

    /** 当前条件下待结算合计。 */
    @Schema(description = "当前条件下待结算合计")
    /** 当前条件下待结算合计（业务字段）。 */
    private BigDecimal pendingSettlementTotal;

    /** 首次加载是否默认全选。 */
    @Schema(description = "首次加载是否默认全选")
    /** 首次加载是否默认全选（业务字段）。 */
    private Boolean defaultSelectAll;
}
