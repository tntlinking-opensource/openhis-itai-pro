/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.settlement.ExtensionIn;

import lombok.Getter;
import lombok.Setter;

/** 收银落账后携带的单条医保结算凭证（health 中性字段）。 */
@Getter
@Setter
public class InsurancePaymentYbSettlementLineExtensionIn {

    private String settlementId;
    private String chargeBatchNo;
    private String encounterPlatformId;
    private Long settlementRecordId;
    private Long accountId;
    private String status;
}
