/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.reconciliation.response;

import lombok.Data;
import lombok.experimental.Accessors;

/** 本地明细对账流水行（出参）。 */
@Data
@Accessors(chain = true)
public class LocalReconDetailResponse {

    /** 契约业务编号 */
    private String contractBusNo;
    /** 对账开始日期 */
    private String stmtBegndate;
    /** 对账结束日期 */
    private String stmtEnddate;
    /** 9101 上传文件名 */
    private String uploadFilename;
    /** 明细行数 */
    private Integer rowCount;
    /** 不平行数 */
    private Integer mismatchCount;
}
