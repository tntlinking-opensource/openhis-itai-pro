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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.module.querysupport.financial.dto.InpatientSettleCategorySummaryDto;
import com.openhis.health.module.querysupport.financial.dto.InpatientSettleChargeLineDto;
import com.openhis.health.module.querysupport.financial.dto.SettledPaymentBriefDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 已结算单查阅响应（IF-IP-STL-07）。
 */
@Data
@Accessors(chain = true)
/** 已结算单查阅响应。 */
@Schema(description = "已结算单查阅响应")
public class LoadSettledInpatientPaymentViewResponse {

    @Schema(description = "就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 就诊 ID（业务字段）。 */
    private Long encounterId;

    /** 成功结算单列表。 */
    @Schema(description = "成功结算单列表")
    /** 成功结算单列表（业务字段）。 */
    private List<SettledPaymentBriefDto> payments;

    /** 当前展示的单据 ID。 */
    @Schema(description = "当前展示的单据 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 当前展示的单据 ID（业务字段）。 */
    private Long activePaymentId;

    /** 结算时间。 */
    @Schema(description = "结算时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    /** 结算时间（业务字段）。 */
    private LocalDateTime settleTime;

    /** 操作员姓名。 */
    @Schema(description = "操作员姓名")
    /** 操作员姓名（业务字段）。 */
    private String operatorName;

    /** 该单应收合计。 */
    @Schema(description = "该单应收合计")
    /** 该单应收合计（业务字段）。 */
    private BigDecimal receivableTotal;

    /** 支付方式展示。 */
    @Schema(description = "支付方式展示")
    /** 支付方式展示（业务字段）。 */
    private String payMethodText;

    /** 费用明细快照。 */
    @Schema(description = "费用明细快照")
    /** 费用明细快照（业务字段）。 */
    private List<InpatientSettleChargeLineDto> lines;

    /** 大类汇总快照。 */
    @Schema(description = "大类汇总快照")
    /** 大类汇总快照（业务字段）。 */
    private List<InpatientSettleCategorySummaryDto> categories;
}
