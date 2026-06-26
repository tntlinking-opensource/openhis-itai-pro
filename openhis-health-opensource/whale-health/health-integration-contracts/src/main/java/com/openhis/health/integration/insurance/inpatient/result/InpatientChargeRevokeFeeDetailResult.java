/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inpatient.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2302 住院费用明细撤销结果。 */
@Data
@Accessors(chain = true)
public class InpatientChargeRevokeFeeDetailResult {

    /** 成功撤销的平台费用行条数。 */
    private int revokedCount;
}
