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

/** 【3203A】申请吉林清算 input */
public class Recon3203AInput extends ChsDataInput<Recon3203AInput.Recon3203AData> {

    /** 3203A input.data */
    @Data
    @Accessors(chain = true)
    public static class Recon3203AData {

        @JSONField(name = "clr_type")
        private String clrType;

        @JSONField(name = "medfee_sumamt")
        private BigDecimal medfeeSumamt;

        @JSONField(name = "med_sumfee")
        private BigDecimal medSumfee;

        @JSONField(name = "fund_appy_sum")
        private BigDecimal fundAppySum;

        @JSONField(name = "cash_payamt")
        private BigDecimal cashPayamt;

        @JSONField(name = "acct_pay")
        private BigDecimal acctPay;

        private String begndate;

        private String enddate;

        @JSONField(name = "clr_optins")
        private String clrOptins;
    }
}
