/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.client;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/** 医保平台返回体 */
@Data
public class ResultBody {

    @JSONField(name = "infcode")
    private String infcode;
    @JSONField(name = "warnMsg")
    private String warnMsg;
    @JSONField(name = "err_msg")
    private String errMsg;
    @JSONField(name = "output")
    private String output;
}
