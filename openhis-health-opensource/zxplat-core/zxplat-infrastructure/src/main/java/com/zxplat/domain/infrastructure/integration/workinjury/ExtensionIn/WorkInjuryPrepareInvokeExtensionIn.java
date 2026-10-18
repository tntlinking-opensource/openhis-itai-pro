/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.workinjury.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/** 工伤 Win invoke prepare 入参。 */
@Data
@Accessors(chain = true)
public class WorkInjuryPrepareInvokeExtensionIn {

    private String sessionId;
    private String phase;
    private Long encounterId;
    private Long contractId;
    private Long paymentDraftId;
    private Long operatorId;
    private Map<String, Object> context;
}
