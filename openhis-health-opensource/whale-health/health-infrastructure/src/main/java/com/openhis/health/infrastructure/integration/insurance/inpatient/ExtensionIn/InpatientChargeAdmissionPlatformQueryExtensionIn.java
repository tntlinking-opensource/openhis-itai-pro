/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 批量查询入院平台登记状态入参。 */
@Data
@Accessors(chain = true)
public class InpatientChargeAdmissionPlatformQueryExtensionIn {

    private List<Long> encounterIds;
}
