/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.outpatient.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2205 撤销未正式结算费用明细结果。 */
@Data
@Accessors(chain = true)
public class OutpatientChargeRevokeFeeDetailResult {

    private String chargeBatchNo;
    private Boolean revoked;
}
