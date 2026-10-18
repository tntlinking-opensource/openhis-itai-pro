/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.outpatient.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2205 门诊费用明细撤销结果 */
@Data
@Accessors(chain = true)
public class OpRevokeFeeDetailResult {

    private String chrgBchno;
    private boolean revoked;
}
