/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.gateway.scheme.settle.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 【2205】处方明细撤销 input（10-P0 §8.1；has_active_fee_upload 时条件插入）。
 */
@Data
@Accessors(chain = true)
public class Wi2205Input {

    /** 门诊/住院流水号；整单撤销仅传此字段。 */
    @JSONField(name = "ipt_otp_no")
    private String iptOtpNo;

    /** 处方号（按手册整单/单行撤销）。 */
    @JSONField(name = "rxno")
    private String rxno;

    /** 处方流水号 feedetl_sn。 */
    @JSONField(name = "feedetl_sn")
    private String feedetlSn;
}
