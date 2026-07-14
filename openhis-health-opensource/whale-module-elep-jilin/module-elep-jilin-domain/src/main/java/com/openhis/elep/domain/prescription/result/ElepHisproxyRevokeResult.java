/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** A0004 撤销结果（Bridge API）。 */
@Data
@Accessors(chain = true)
public class ElepHisproxyRevokeResult {

    private boolean success;
    private String message;
    private Long prescriptionId;
}
