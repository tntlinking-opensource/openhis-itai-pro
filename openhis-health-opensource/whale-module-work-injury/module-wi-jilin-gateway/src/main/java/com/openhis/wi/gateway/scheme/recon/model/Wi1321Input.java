/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.gateway.scheme.recon.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 【1321】工伤明细对账入参（手册按就诊流水；院点月对账链默认不调用）。
 */
@Data
@Accessors(chain = true)
public class Wi1321Input {

    /** 门诊/住院流水号。 */
    @JSONField(name = "ipt_otp_no")
    private String iptOtpNo;

    /** 单据号（手册示例传 0）。 */
    @JSONField(name = "medrcdno")
    private String medrcdno;

    /** 统筹区编码。 */
    @JSONField(name = "social_pool_code")
    private String socialPoolCode;
}
