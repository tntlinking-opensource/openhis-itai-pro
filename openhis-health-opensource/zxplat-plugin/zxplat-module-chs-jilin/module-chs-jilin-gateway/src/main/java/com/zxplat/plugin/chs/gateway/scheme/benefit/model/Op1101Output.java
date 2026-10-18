/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.benefit.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 【1101】人员信息获取 output */
@Data
@Accessors(chain = true)
public class Op1101Output {

    private Op1101Baseinfo baseinfo;

    private List<Op1101InsuInfoRow> insuinfo;

    private List<Op1101IdetInfoRow> idetinfo;
}
