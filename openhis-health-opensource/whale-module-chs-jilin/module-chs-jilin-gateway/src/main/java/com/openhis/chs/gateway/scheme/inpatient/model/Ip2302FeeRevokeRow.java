/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.inpatient.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/** 2302 input.data 单行（费用明细撤销） */
@Data
@Accessors(chain = true)
public class Ip2302FeeRevokeRow {

    @JSONField(name = "feedetl_sn")
    private String feedetlSn;

    @JSONField(name = "mdtrt_id")
    private String mdtrtId;

    @JSONField(name = "psn_no")
    private String psnNo;
}
