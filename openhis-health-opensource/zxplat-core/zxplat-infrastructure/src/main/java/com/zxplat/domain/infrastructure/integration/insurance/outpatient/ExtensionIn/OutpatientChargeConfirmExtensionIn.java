/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/** 门诊正式结算确认命令（本地落账前调 Extension 2207）。 */
@Data
@Accessors(chain = true)
public class OutpatientChargeConfirmExtensionIn {

    /** 收银草稿 id。 */
    private Long paymentDraftId;
    /** 收费批次号 chrg_bchno（混合结算逐组 confirm）。 */
    private String chargeBatchNo;
    /** 就诊 id。 */
    private Long encounterId;
}
