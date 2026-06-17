/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.querysupport.financial;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 住院结算摘要读查询（IF-IP-STL-03）。
 */
@Data
@Accessors(chain = true)
public class InpatientSettleSummaryQuery {

    private Long encounterId;
    private Long patientId;
    private List<Long> selectedChargeItemIds;
}
