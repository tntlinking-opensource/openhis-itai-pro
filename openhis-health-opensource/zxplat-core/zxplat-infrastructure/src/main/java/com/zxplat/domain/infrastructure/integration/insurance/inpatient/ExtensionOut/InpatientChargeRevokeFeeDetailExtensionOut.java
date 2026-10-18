/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2302 住院费用明细撤销结果。 */
@Data
@Accessors(chain = true)
public class InpatientChargeRevokeFeeDetailExtensionOut {

    /** 成功撤销的平台费用行条数。 */
    private int revokedCount;
}
