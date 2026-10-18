/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 取药结果查询 Client 结果。 */
@Data
@Accessors(chain = true)
public class ElepMedResultResult {

    private boolean success;
    private String medResultJson;
    private String message;
}
