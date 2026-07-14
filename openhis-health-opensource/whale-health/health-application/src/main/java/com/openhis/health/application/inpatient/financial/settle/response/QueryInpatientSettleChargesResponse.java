/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.settle.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inpatient.financial.settle.support.dto.InpatientSettleCategorySummaryDto;
import com.openhis.health.application.inpatient.financial.settle.support.dto.InpatientSettleChargeLineDto;
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

    /** 就诊 ID（业务字段）。 */
    @Schema(description = "就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 费用呈现模式 code。 */
    @Schema(description = "费用呈现模式 code")
    private String presentationMode;

    /** 明细行列表。 */
    @Schema(description = "明细行列表（DETAIL 模式）")
    private List<InpatientSettleChargeLineDto> lines;

    /** 大类汇总列表。 */
    @Schema(description = "大类汇总列表（SUMMARY 模式）")
    private List<InpatientSettleCategorySummaryDto> categories;

    /** 当前条件下待结算合计。 */
    @Schema(description = "当前条件下待结算合计")
    private BigDecimal pendingSettlementTotal;

    /** 首次加载是否默认全选。 */
    @Schema(description = "首次加载是否默认全选")
    private Boolean defaultSelectAll;
}
