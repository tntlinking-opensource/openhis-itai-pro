/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 【3202】明细对账出参（含 9102 解析结果行） */
@Data
@Accessors(chain = true)
public class ReconDetail3202Result {

    private Long localRecordId;
    private String uploadFileQuryNo;
    private String uploadFilename;
    private String resultFileQuryNo;
    private String resultFilename;
    private String dldEndtime;
    private Integer rowCount;
    private Integer mismatchCount;
    private List<ReconDetailResultRowResult> resultRows;
}
