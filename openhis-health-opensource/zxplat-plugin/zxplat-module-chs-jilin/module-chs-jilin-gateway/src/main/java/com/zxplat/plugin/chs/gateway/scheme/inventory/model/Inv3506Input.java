/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.inventory.model;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【3506】销售退货上传 input */
@Data
@Accessors(chain = true)
public class Inv3506Input {

    private InvSelinfo selinfo;
}
