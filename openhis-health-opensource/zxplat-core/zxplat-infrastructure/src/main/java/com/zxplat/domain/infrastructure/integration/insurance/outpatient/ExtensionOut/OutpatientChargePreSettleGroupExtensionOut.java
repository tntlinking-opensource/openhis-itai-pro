/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/** 混合预结算单组结果（含分组维度 accountId/medType/diseCodg）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class OutpatientChargePreSettleGroupExtensionOut extends OutpatientChargePreSettleExtensionOut {

    /** 组序号（0-based，与 confirm 顺序一致）。 */
    private Integer groupIndex;
    /** 本组医疗类别。 */
    private String medType;
    /** 慢病病种代码 dise_codg（普门为空）。 */
    private String diseCodg;
    /** 费用账户 id（多账户混合场景）。 */
    private Long accountId;
    /** 本组 chargeItem id 列表。 */
    private List<Long> chargeItemIds;
}
