/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.settle.support;

import com.openhis.health.application.inpatient.financial.settle.support.dto.InpatientSettleCategorySummaryDto;
import com.openhis.health.application.inpatient.financial.settle.support.dto.InpatientSettleChargeLineDto;
import com.openhis.health.application.inpatient.financial.settle.support.dto.SettledPaymentBriefDto;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 已结算单查阅读结果（IF-IP-STL-07；含 AS-IP-FIN-004 netSettleTotal）。
 */
@Data
@Accessors(chain = true)
public class SettledInpatientPaymentViewResult {

    /** 住院就诊主键。 */
    private Long encounterId;

    /** 本就诊结算/反结历史摘要列表（含 signedAmount 与 reverseEnabled）。 */
    private List<SettledPaymentBriefDto> payments;

    /** 当前展示的历史 payment 主键。 */
    private Long activePaymentId;

    /** 当前展示单的结算时间（bill_date）。 */
    private LocalDateTime settleTime;

    /** 当前展示单操作员展示名。 */
    private String operatorName;

    /** 当前展示单应收合计（绝对值）。 */
    private BigDecimal receivableTotal;

    /** 当前展示单支付方式展示文案。 */
    private String payMethodText;

    /** 当前展示单 chargeItem 明细；summary 模式时为 null。 */
    private List<InpatientSettleChargeLineDto> lines;

    /** 当前展示单大类汇总；detail 模式时为 null。 */
    private List<InpatientSettleCategorySummaryDto> categories;

    /** 本就诊住院结算流水 signedAmount 代数和（一正一负抵净）。 */
    private BigDecimal netSettleTotal;
}
