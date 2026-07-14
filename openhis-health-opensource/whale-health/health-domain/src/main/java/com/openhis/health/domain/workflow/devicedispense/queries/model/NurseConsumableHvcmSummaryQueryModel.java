/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicedispense.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 护士耗材使用量按高/低值汇总行（AS-RPT-002 IF-RPT-NWL-04）。
 */
@Getter
@Setter
public class NurseConsumableHvcmSummaryQueryModel {

    /** 高值耗材标志（{@code DeviceDefinition.hvcm_flag}；null 表示目录未 enrich）。 */
    private Boolean hvcmFlag;

    /** 发放数量合计（{@code COALESCE(dispense_quantity, quantity)} 代数 SUM）。 */
    private BigDecimal quantityTotal = BigDecimal.ZERO;
}
