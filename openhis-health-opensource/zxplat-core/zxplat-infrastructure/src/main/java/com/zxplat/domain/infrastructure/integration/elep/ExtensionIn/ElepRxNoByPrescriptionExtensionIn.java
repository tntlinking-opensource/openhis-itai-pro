/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.elep.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/** 按处方组查 hi_rxno 入参。 */
@Data
@Accessors(chain = true)
public class ElepRxNoByPrescriptionExtensionIn {

    private Long prescriptionGroupId;
}
