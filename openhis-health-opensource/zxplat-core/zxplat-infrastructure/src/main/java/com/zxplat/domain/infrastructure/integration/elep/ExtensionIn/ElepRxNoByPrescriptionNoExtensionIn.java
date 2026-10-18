/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.elep.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/** 按院内处方号查 hi_rxno 入参。 */
@Data
@Accessors(chain = true)
public class ElepRxNoByPrescriptionNoExtensionIn {

    private String prescriptionNo;
}
