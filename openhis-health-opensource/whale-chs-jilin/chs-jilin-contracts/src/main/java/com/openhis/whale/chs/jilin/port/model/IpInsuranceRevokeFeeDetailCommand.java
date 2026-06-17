/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 2302 住院费用明细撤销命令。 */
@Data
@Accessors(chain = true)
public class IpInsuranceRevokeFeeDetailCommand {

    private Long encounterId;
    private Long contractId;
    private List<Long> chargeItemIds;
}
