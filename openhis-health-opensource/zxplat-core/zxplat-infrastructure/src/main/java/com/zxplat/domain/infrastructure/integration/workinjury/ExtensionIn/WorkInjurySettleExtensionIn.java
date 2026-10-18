/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.workinjury.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/** 工伤门诊预结算/结算会话启动入参。 */
@Data
@Accessors(chain = true)
public class WorkInjurySettleExtensionIn {

    private Long encounterId;
    private Long contractId;
    private Long paymentDraftId;
    private Long operatorId;
    private List<Long> chargeItemIds;
    private Map<String, Object> context;
}
