/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.chargeitem.query;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 工作量 fin_type 汇总行（领域层按开单科室 + 定价 definition 聚合；DM-ADM-011 §6.2.27）。
 *
 * <p>应用层映射 {@code fin_type_code} 后，未选科室时按 fin_type **跨 {@link #requestingOrgId} 合并**
 *（AS-RPT-001 BR-RPT-WKL-016）；实收/成本/净收入不在本读模型内。</p>
 */
@Getter
@Setter
public class PractitionerWorkloadFinTypeOrgSummaryQueryModel {

    /** 开单科室 {@code adm_charge_item.requesting_org_id}。 */
    private Long requestingOrgId;

    /** 定价锚点 {@code adm_charge_item.definition_id}（应用层解析 fin_type_code）。 */
    private Long definitionId;

    /** 应收合计（账单折后应向患者收取；代数和，含退费负向）。 */
    private BigDecimal receivableAmountTotal = BigDecimal.ZERO;

    /** 折后应收合计（与门诊费用明细读侧同一 resolve 口径）。 */
    private BigDecimal discountedAmountTotal = BigDecimal.ZERO;

    /** 行金额合计（{@code total_price} 代数和）。 */
    private BigDecimal lineAmountTotal = BigDecimal.ZERO;
}
