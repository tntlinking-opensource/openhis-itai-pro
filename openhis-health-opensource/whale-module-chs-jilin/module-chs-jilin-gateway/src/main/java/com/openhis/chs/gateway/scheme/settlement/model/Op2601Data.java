/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.settlement.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【2601】冲正原交易 input.data */
@Data
@Accessors(chain = true)
public class Op2601Data {

    @JSONField(name = "psn_no")
    private String psnNo;

    private String omsgid;

    private String oinfno;
}
