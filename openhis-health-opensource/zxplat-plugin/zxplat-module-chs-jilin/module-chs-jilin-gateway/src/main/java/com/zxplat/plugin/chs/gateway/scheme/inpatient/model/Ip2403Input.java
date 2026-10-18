/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.inpatient.model;

import com.zxplat.plugin.chs.gateway.scheme.common.model.ChsDiseInfoRow;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 【2403】住院信息变更 input（adminfo + diseinfo） */
@Data
@Accessors(chain = true)
public class Ip2403Input {

    private Ip2403Adminfo adminfo;

    private List<ChsDiseInfoRow> diseinfo;
}
