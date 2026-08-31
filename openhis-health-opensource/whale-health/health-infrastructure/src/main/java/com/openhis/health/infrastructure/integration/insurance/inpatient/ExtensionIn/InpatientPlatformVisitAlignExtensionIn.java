/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/** 住院平台在院态诊断/修复入参。 */
@Data
@Accessors(chain = true)
public class InpatientPlatformVisitAlignExtensionIn {

    private Long encounterId;
    private Long contractId;
    private String insuredPersonNo;
    private String insuplcAdmdvs;
    private Long operatorId;
}
