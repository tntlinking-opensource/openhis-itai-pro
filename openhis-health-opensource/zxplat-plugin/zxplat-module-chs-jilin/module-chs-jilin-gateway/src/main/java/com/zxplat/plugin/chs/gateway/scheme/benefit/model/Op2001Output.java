/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.benefit.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 【2001】人员待遇享受检查 output */
@Data
@Accessors(chain = true)
public class Op2001Output {

    private List<Op2001TrtinfoRow> trtinfo;
}
