/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.inpatient.model;

import com.zxplat.plugin.chs.gateway.scheme.common.model.ChsSettleData;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【2303】住院预结算 input（data） */
@Data
@Accessors(chain = true)
public class Ip2303Input {

    private ChsSettleData data;
}
