/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.outpatient.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.zxplat.plugin.chs.gateway.scheme.common.model.wrapper.ChsDataInput;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【2202】门诊挂号撤销 input */
public class Op2202Input extends ChsDataInput<Op2202Input.Op2202Data> {

    /** 2202 input.data */
    @Data
    @Accessors(chain = true)
    public static class Op2202Data {

        @JSONField(name = "psn_no")
        private String psnNo;

        @JSONField(name = "mdtrt_id")
        private String mdtrtId;

        @JSONField(name = "ipt_otp_no")
        private String iptOtpNo;
    }
}
