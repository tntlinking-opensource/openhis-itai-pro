/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【3201】日对账出参 */
@Data
@Accessors(chain = true)
public class ReconDaily3201Result {

    private String setlOptins;
    private String stmtRslt;
    private String stmtRsltDscr;
    private Long localRecordId;
}
