/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.benefit.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【2001】待遇检查 output.trtinfo 行 */
@Data
@Accessors(chain = true)
public class Op2001TrtinfoRow {

    @JSONField(name = "psn_no")
    private String psnNo;

    @JSONField(name = "trt_chk_type")
    private String trtChkType;

    @JSONField(name = "fund_pay_type")
    private String fundPayType;

    @JSONField(name = "trt_enjymnt_flag")
    private String trtEnjymntFlag;

    private String begndate;

    private String enddate;

    @JSONField(name = "trt_chk_rslt")
    private String trtChkRslt;
}
