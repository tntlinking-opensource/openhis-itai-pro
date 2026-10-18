/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.reconciliation.response;

import lombok.Data;
import lombok.experimental.Accessors;

/** 本地明细对账流水行（出参）。 */
@Data
@Accessors(chain = true)
public class LocalReconDetailResponse {

    /** 本地记录 ID */
    private Long id;
    /** 契约业务编号 */
    private String contractBusNo;
    /** 结算经办机构 */
    private String setlOptins;
    /** 清算类别 */
    private String clrType;
    /** 对账开始日期 */
    private String stmtBegndate;
    /** 对账结束日期 */
    private String stmtEnddate;
    /** 明细行数 */
    private Integer rowCount;
    /** 不平行数 */
    private Integer mismatchCount;
    /** 9101 上传文件查询号 */
    private String uploadFileQuryNo;
    /** 9102 结果文件查询号 */
    private String resultFileQuryNo;
    /** 创建时间 */
    private String createdAt;
}
