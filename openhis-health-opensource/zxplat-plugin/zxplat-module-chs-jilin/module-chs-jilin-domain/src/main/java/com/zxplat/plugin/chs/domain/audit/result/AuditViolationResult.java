/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.audit.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 3101 单条违规头（output.result 元素） */
@Data
@Accessors(chain = true)
public class AuditViolationResult {

    /** 违规结果 ID */
    private String jrId;
    /** 规则 ID */
    private String ruleId;
    /** 规则名称 */
    private String ruleName;
    /** 违规内容 */
    private String volaCont;
    /** 参保人 ID */
    private String patnId;
    /** 就诊 ID */
    private String mdtrtId;
    /** 违规金额 */
    private BigDecimal volaAmt;
    /** 违规金额计算状态 */
    private String volaAmtStas;
    /** 严重程度 */
    private String sevDeg;
    /** 违规依据 */
    private String volaEvid;
    /** 违规行为分类 */
    private String volaBhvrType;
    /** 违规明细行 */
    private List<AuditViolationDetailResult> details = new ArrayList<>();
}
