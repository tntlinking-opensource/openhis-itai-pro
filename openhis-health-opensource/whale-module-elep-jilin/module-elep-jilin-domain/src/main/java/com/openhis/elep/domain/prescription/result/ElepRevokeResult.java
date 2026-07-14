/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 电子处方撤销 Client 结果。 */
@Data
@Accessors(chain = true)
public class ElepRevokeResult {

    private boolean success;
    private String message;
}
