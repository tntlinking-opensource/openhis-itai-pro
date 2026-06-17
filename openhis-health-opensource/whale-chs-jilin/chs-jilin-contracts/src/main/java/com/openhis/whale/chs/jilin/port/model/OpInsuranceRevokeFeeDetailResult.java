/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port.model;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2205 门诊费用明细撤销结果。 */
@Data
@Accessors(chain = true)
public class OpInsuranceRevokeFeeDetailResult {

    private String chrgBchno;
    private boolean revoked;
}
