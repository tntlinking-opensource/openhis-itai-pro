/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊撤销费用明细命令（撤销 2204 前本地挂接）。
 */
@Data
@Accessors(chain = true)
public class OutpatientChargeRevokeFeeDetailExtensionIn {

    /** 收费批次号 chrg_bchno。 */
    private String chargeBatchNo;

    /** 收银草稿主键。 */
    private Long paymentDraftId;
}
