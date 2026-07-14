/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.platform.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 2204/2301 费用上传平台 result 行（domain 值对象；由 gateway Parser 填充，{@code FeeUploadResultApplier} 写上传行） */
@Data
@Accessors(chain = true)
public class ChsFeeUploadResultRow {

    @JSONField(name = "feedetl_sn")
    private String feedetlSn;

    @JSONField(name = "chrgitm_lv")
    private String chrgitmLv;

    @JSONField(name = "med_chrgitm_type")
    private String medChrgitmType;

    @JSONField(name = "selfpay_prop")
    private BigDecimal selfpayProp;

    @JSONField(name = "fulamt_ownpay_amt")
    private BigDecimal fulamtOwnpayAmt;

    @JSONField(name = "overlmt_amt")
    private BigDecimal overlmtAmt;

    @JSONField(name = "preselfpay_amt")
    private BigDecimal preselfpayAmt;

    @JSONField(name = "inscp_scp_amt")
    private BigDecimal inscpScpAmt;

    @JSONField(name = "lmt_used_flag")
    private String lmtUsedFlag;

    private String memo;
}
