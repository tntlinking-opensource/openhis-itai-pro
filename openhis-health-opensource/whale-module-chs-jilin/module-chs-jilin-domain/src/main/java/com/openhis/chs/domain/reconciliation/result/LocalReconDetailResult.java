/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 本地明细对账流水出参 */
@Data
@Accessors(chain = true)
public class LocalReconDetailResult {

    private Long id;
    private String contractBusNo;
    private String setlOptins;
    private String clrType;
    private String stmtBegndate;
    private String stmtEnddate;
    private Integer rowCount;
    private Integer mismatchCount;
    private String uploadFileQuryNo;
    private String resultFileQuryNo;
    private String createdAt;
}
