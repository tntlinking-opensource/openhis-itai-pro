/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊撤销费用明细结果。
 */
@Data
@Accessors(chain = true)
public class OutpatientChargeRevokeFeeDetailExtensionOut {

    /** 已撤销的收费批次号。 */
    private String chargeBatchNo;

    /** 是否已成功撤销。 */
    private Boolean revoked;
}
