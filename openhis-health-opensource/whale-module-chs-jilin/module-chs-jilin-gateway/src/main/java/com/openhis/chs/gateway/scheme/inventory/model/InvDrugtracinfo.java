/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.inventory.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/** 进销存追溯码节点（3502/3505/3506 drugtracinfo 数组元素） */
@Data
@Accessors(chain = true)
public class InvDrugtracinfo {

    @JSONField(name = "drug_trac_codg")
    private String drugTracCodg;
}
