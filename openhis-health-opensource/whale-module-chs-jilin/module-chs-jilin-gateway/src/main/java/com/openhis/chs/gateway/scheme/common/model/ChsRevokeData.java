/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.common.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/** 2205/2208/2305/2404/2405 input.data 撤销体 */
@Data
@Accessors(chain = true)
public class ChsRevokeData {

    @JSONField(name = "mdtrt_id")
    private String mdtrtId;

    @JSONField(name = "setl_id")
    private String setlId;

    @JSONField(name = "psn_no")
    private String psnNo;

    @JSONField(name = "chrg_bchno")
    private String chrgBchno;
}
