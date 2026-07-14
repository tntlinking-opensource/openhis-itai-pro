/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2401 入院登记结果。 */
@Data
@Accessors(chain = true)
public class InpatientChargeAdmissionExtensionOut {

    /** 平台就诊 ID mdtrt_id。 */
    private String encounterPlatformId;
}
