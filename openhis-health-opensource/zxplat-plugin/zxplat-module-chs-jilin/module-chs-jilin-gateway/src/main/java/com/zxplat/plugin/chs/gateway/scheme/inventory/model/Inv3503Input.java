/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.inventory.model;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【3503】采购上传 input */
@Data
@Accessors(chain = true)
public class Inv3503Input {

    private InvPurcinfo purcinfo;
}
