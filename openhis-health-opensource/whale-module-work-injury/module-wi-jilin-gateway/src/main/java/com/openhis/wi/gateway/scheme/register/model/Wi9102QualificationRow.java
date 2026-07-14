/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.gateway.scheme.register.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 【9102】审批信息单行 output（10-P0 §4.2）。
 */
@Data
@Accessors(chain = true)
public class Wi9102QualificationRow {

    /** 审批资格 ID；UI 选择键 → anchor。 */
    @JSONField(name = "qualification_id")
    private String qualificationId;

    /** 审批类型 approveType。 */
    @JSONField(name = "approveType")
    private String approveType;

    /** 认定结论 injured_injured_conclusion。 */
    @JSONField(name = "injured_injured_conclusion")
    private String injuredInjuredConclusion;

    /** 有效期开始 yyyyMMdd。 */
    @JSONField(name = "startDate")
    private String startDate;

    /** 有效期结束 yyyyMMdd。 */
    @JSONField(name = "endDate")
    private String endDate;

    /** 待遇校验 trt_chk_rslt；无审批时提示。 */
    @JSONField(name = "trt_chk_rslt")
    private String trtChkRslt;

    /** 人员编号 psn_no。 */
    @JSONField(name = "psn_no")
    private String psnNo;

    /** 人员姓名 psn_name。 */
    @JSONField(name = "psn_name")
    private String psnName;
}
