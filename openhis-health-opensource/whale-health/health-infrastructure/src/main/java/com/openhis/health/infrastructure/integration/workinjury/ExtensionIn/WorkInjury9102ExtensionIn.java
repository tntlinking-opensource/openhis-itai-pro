/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.workinjury.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/** 工伤 9102 审批查询 Extension 入参。 */
@Data
@Accessors(chain = true)
public class WorkInjury9102ExtensionIn {

    private Long encounterId;
    private Long contractId;
    private Long operatorId;
}
