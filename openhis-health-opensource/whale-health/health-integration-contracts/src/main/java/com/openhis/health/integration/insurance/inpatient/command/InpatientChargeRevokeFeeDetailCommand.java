/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 住院费用明细撤销命令（Extension 2302）。 */
@Data
@Accessors(chain = true)
public class InpatientChargeRevokeFeeDetailCommand {

    /** 就诊 id。 */
    private Long encounterId;
    /** 费用性质 id。 */
    private Long contractId;
    /** 待撤销 chargeItem id 列表。 */
    private List<Long> chargeItemIds = new ArrayList<>();
}
