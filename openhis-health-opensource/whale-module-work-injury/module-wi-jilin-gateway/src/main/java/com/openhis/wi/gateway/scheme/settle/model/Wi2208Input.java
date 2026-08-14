/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.gateway.scheme.settle.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 【2208】费用结算撤销 input（手册 V1.3：ipt_otp_no / mdtrt_id / setl_time / keep_tp_type）。
 */
@Data
@Accessors(chain = true)
public class Wi2208Input {

    /** 门诊/住院流水号。 */
    @JSONField(name = "ipt_otp_no")
    private String iptOtpNo;

    /** 平台就诊 ID mdtrt_id。 */
    @JSONField(name = "mdtrt_id")
    private String mdtrtId;

    /** 原结算时间 yyyyMMddHHmmss。 */
    @JSONField(name = "setl_time")
    private String setlTime;

    /** 是否保留处方：0 不保留 / 1 保留。 */
    @JSONField(name = "keep_tp_type")
    private String keepTpType;
}
