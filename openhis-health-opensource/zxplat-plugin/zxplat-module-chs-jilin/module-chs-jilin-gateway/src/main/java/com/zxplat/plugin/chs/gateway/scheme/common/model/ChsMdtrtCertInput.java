/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.common.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/** 1101 input.data 就诊凭证 */
@Data
@Accessors(chain = true)
public class ChsMdtrtCertInput {

    @JSONField(name = "mdtrt_cert_type")
    private String mdtrtCertType;

    @JSONField(name = "mdtrt_cert_no")
    private String mdtrtCertNo;

    @JSONField(name = "card_sn")
    private String cardSn;

    @JSONField(name = "psn_cert_type")
    private String psnCertType;

    private String certno;

    @JSONField(name = "psn_name")
    private String psnName;
}
