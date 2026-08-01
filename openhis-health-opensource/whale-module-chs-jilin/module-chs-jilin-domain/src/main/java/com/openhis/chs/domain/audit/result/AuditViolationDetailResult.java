/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.audit.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 3101 违规明细（judge_result_detail_dtos 元素） */
@Data
@Accessors(chain = true)
public class AuditViolationDetailResult {

    /** 明细结果 ID（3103 warn_rslt_id） */
    private String jrdId;
    /** 参保人 ID */
    private String patnId;
    /** 就诊 ID */
    private String mdtrtId;
    /** 处方/医嘱 ID */
    private String rxId;
    /** 违规项目类型 */
    private String volaItemType;
    /** 违规金额 */
    private BigDecimal volaAmt;
}
