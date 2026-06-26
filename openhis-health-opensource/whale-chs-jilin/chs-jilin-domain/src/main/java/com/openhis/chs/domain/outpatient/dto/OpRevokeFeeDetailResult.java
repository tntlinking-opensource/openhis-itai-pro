/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.outpatient.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2205 门诊费用明细撤销结果。 */
@Data
@Accessors(chain = true)
public class OpRevokeFeeDetailResult {

    private String chrgBchno;
    private boolean revoked;
}
