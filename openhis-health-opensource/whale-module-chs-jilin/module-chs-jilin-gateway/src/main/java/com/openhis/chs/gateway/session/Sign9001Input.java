/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.session;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Builder;
import lombok.Data;

/**
 * 【9001】签到 signIn 节点。
 */
@Data
@Builder
public class Sign9001Input {

    @JSONField(name = "opter_no")
    private String opterNo;

    private String mac;

    private String ip;
}
