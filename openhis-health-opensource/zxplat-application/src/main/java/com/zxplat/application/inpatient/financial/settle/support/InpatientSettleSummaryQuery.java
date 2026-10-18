/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.settle.support;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 住院结算摘要读查询（IF-IP-STL-03）。
 */
@Data
@Accessors(chain = true)
public class InpatientSettleSummaryQuery {

    /** 住院就诊主键。 */
    private Long encounterId;

    /** 患者主键；须与就诊归属一致（Gate）。 */
    private Long patientId;

    /** 收银台已勾选待结算 chargeItem 主键；空表示按全量待结算合计。 */
    private List<Long> selectedChargeItemIds;
}
