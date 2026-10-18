/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.inpatient.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【2401】入院办理 output.result */
@Data
@Accessors(chain = true)
public class Ip2401Output {

    @JSONField(name = "mdtrt_id")
    private String mdtrtId;
}
