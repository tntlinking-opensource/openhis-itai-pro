/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.outpatient.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【2201】门诊挂号 output.data */
@Data
@Accessors(chain = true)
public class Op2201OutputData {

    @JSONField(name = "mdtrt_id")
    private String mdtrtId;
}
