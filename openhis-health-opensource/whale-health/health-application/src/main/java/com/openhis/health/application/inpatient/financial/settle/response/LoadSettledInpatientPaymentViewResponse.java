/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.settle.response;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inpatient.financial.settle.support.dto.InpatientSettleCategorySummaryDto;
import com.openhis.health.application.inpatient.financial.settle.support.dto.InpatientSettleChargeLineDto;
import com.openhis.health.application.inpatient.financial.settle.support.dto.SettledPaymentBriefDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 已结算单查阅响应（IF-IP-STL-07；AS-IP-FIN-004 历史 NET 增量）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "已结算单查阅响应")
public class LoadSettledInpatientPaymentViewResponse {

    /** 住院就诊 ID。 */
    @Schema(description = "就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 住院结算/反结历史摘要行（含正向与逆向）。 */
    @Schema(description = "住院结算/反结历史列表")
    private List<SettledPaymentBriefDto> payments;

    /** 当前选中展示快照的支付单 ID。 */
    @Schema(description = "当前展示的单据 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activePaymentId;

    /** 当前选中单的结算/反结时间。 */
    @Schema(description = "结算时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime settleTime;

    /** 当前选中单经办操作员展示名。 */
    @Schema(description = "操作员姓名")
    private String operatorName;

    /** 当前选中单应收合计（绝对值，不含反结符号）。 */
    @Schema(description = "该单应收合计")
    private BigDecimal receivableTotal;

    /** 当前选中单支付方式展示文案。 */
    @Schema(description = "支付方式展示")
    private String payMethodText;

    /** 当前选中单绑定费用明细只读快照。 */
    @Schema(description = "费用明细快照")
    private List<InpatientSettleChargeLineDto> lines;

    /** 当前选中单大类汇总只读快照。 */
    @Schema(description = "大类汇总快照")
    private List<InpatientSettleCategorySummaryDto> categories;

    /** 本就诊历史行 signedAmount 代数和（一正一负抵净）。 */
    @Schema(description = "结算历史净额")
    private BigDecimal netSettleTotal;
}
