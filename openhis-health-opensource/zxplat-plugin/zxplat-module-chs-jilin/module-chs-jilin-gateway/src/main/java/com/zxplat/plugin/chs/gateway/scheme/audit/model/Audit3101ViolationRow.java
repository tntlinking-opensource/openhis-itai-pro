/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.audit.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/** 3101 output.result 元素 */
@Data
@Accessors(chain = true)
public class Audit3101ViolationRow {

    @JSONField(name = "jr_id")
    private String jrId;

    @JSONField(name = "rule_id")
    private String ruleId;

    @JSONField(name = "rule_name")
    private String ruleName;

    @JSONField(name = "vola_cont")
    private String volaCont;

    @JSONField(name = "patn_id")
    private String patnId;

    @JSONField(name = "mdtrt_id")
    private String mdtrtId;

    @JSONField(name = "vola_amt")
    private BigDecimal volaAmt;

    @JSONField(name = "vola_amt_stas")
    private String volaAmtStas;

    @JSONField(name = "sev_deg")
    private String sevDeg;

    @JSONField(name = "vola_evid")
    private String volaEvid;

    @JSONField(name = "vola_bhvr_type")
    private String volaBhvrType;

    @JSONField(name = "judge_result_detail_dtos")
    private List<Audit3101DetailRow> judgeResultDetailDtos;
}
