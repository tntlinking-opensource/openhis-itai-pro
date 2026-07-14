/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【A0006】取药结果查询结果（Bridge）。 */
@Data
@Accessors(chain = true)
public class ElepPlatformMedResultResult {

    private boolean success;
    private String message;
    private String medResultJson;
}
