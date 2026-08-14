/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.gateway.scheme.recon.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 【1320】工伤总额对账入参（10-P0 §11）。
 */
@Data
@Accessors(chain = true)
public class Wi1320Input {

    /** 对账开始日期 yyyyMMdd。 */
    @JSONField(name = "stmt_begindate")
    private String stmtBegindate;

    /** 对账结束日期 yyyyMMdd。 */
    @JSONField(name = "stmt_enddate")
    private String stmtEnddate;

    /** 统筹区编码（院点 220100）。 */
    @JSONField(name = "social_pool_code")
    private String socialPoolCode;
}
