/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.reconciliation.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.openhis.chs.gateway.scheme.common.model.wrapper.ChsDataInput;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 【3201】日对账 input */
public class Recon3201Input extends ChsDataInput<Recon3201Input.Recon3201Data> {

    /** 3201 input.data */
    @Data
    @Accessors(chain = true)
    public static class Recon3201Data {

        private String insutype;

        @JSONField(name = "clr_type")
        private String clrType;

        @JSONField(name = "setl_optins")
        private String setlOptins;

        @JSONField(name = "stmt_begndate")
        private String stmtBegndate;

        @JSONField(name = "stmt_enddate")
        private String stmtEnddate;

        @JSONField(name = "medfee_sumamt")
        private BigDecimal medfeeSumamt;

        @JSONField(name = "fund_pay_sumamt")
        private BigDecimal fundPaySumamt;

        @JSONField(name = "acct_pay")
        private BigDecimal acctPay;

        @JSONField(name = "fixmedins_setl_cnt")
        private Integer fixmedinsSetlCnt;
    }
}
