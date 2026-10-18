/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.audit.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 3101 judge_result_detail_dtos 元素 */
@Data
@Accessors(chain = true)
public class Audit3101DetailRow {

    @JSONField(name = "jrd_id")
    private String jrdId;

    @JSONField(name = "patn_id")
    private String patnId;

    @JSONField(name = "mdtrt_id")
    private String mdtrtId;

    @JSONField(name = "rx_id")
    private String rxId;

    @JSONField(name = "vola_item_type")
    private String volaItemType;

    @JSONField(name = "vola_amt")
    private BigDecimal volaAmt;
}
