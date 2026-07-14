/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.gateway.scheme.settle.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 【2208】费用结算撤销 input（10-P0 §10）。
 */
@Data
@Accessors(chain = true)
public class Wi2208Input {

    /** 2207 出参 setl_id。 */
    @JSONField(name = "setl_id")
    private String setlId;

    /** 平台就诊 ID mdtrt_id。 */
    @JSONField(name = "mdtrt_id")
    private String mdtrtId;

    /** 人员编号 psn_no。 */
    @JSONField(name = "psn_no")
    private String psnNo;
}
