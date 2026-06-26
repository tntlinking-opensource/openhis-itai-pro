/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 【2302】撤销住院费用明细命令。 */
@Data
@Accessors(chain = true)
public class IpInsuranceRevokeFeeDetailCommand {

    /** 就诊 id。 */
    private Long encounterId;
    /** 费用性质 id。 */
    private Long contractId;
    /** 待撤销 chargeItem id 列表。 */
    private List<Long> chargeItemIds;
}
