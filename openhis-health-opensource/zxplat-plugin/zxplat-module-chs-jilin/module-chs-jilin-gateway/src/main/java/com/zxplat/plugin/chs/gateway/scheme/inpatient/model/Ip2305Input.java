/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.inpatient.model;

import com.zxplat.plugin.chs.gateway.scheme.common.model.ChsRevokeData;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【2305】住院结算撤销 input（data） */
@Data
@Accessors(chain = true)
public class Ip2305Input {

    private ChsRevokeData data;
}
