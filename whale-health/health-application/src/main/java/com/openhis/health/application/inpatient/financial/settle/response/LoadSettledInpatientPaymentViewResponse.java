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
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettleCategorySummaryDto;
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettleChargeLineDto;
import com.openhis.health.application.inpatient.financial.settle.dto.SettledPaymentBriefDto;
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
@Schema(description = "已结算单查阅响应")
public class LoadSettledInpatientPaymentViewResponse {

    @Schema(description = "就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    @Schema(description = "成功结算单列表")
    private List<SettledPaymentBriefDto> payments;

    @Schema(description = "当前展示的单据 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activePaymentId;

    @Schema(description = "结算时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime settleTime;

    @Schema(description = "操作员姓名")
    private String operatorName;

    @Schema(description = "该单应收合计")
    private BigDecimal receivableTotal;

    @Schema(description = "支付方式展示")
    private String payMethodText;

    @Schema(description = "费用明细快照")
    private List<InpatientSettleChargeLineDto> lines;

    @Schema(description = "大类汇总快照")
    private List<InpatientSettleCategorySummaryDto> categories;
}
