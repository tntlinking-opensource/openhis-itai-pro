/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inpatient.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2302 住院费用明细撤销结果 */
@Data
@Accessors(chain = true)
public class IpRevokeFeeDetailResult {

    private int revokedCount;
}
