/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.outpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2205 撤销未正式结算费用明细命令。 */
@Data
@Accessors(chain = true)
public class OutpatientChargeRevokeFeeDetailCommand {

    private String chargeBatchNo;
    private Long paymentDraftId;
}
