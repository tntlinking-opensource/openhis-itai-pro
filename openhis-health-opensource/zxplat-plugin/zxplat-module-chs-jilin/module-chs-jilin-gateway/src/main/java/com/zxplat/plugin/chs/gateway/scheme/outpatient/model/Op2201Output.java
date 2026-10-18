/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.outpatient.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【2201】门诊挂号 output */
@Data
@Accessors(chain = true)
public class Op2201Output {

    private Op2201OutputData data;

    @JSONField(name = "mdtrt_id")
    private String mdtrtId;
}
