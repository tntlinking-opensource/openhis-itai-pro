/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【3203A】清算申请出参 */
@Data
@Accessors(chain = true)
public class ApplyJilinClear3203AResult {

    private String clrAppyEvtId;
    private Long localRecordId;
}
