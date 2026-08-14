/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.gateway.scheme.recon.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 【1322】工伤对账确认入参（10-P0 §11；手册仅 month）。
 */
@Data
@Accessors(chain = true)
public class Wi1322Input {

    /** 对账月份 yyyyMM。 */
    @JSONField(name = "month")
    private String month;

    /** 统筹区编码（院点扩展；手册示例可省略）。 */
    @JSONField(name = "social_pool_code")
    private String socialPoolCode;
}
