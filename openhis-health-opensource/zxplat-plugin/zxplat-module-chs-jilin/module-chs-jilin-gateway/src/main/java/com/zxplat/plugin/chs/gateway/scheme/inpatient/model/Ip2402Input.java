/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.inpatient.model;

import com.zxplat.plugin.chs.gateway.scheme.common.model.ChsDiseInfoRow;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 【2402】出院办理 input（dscginfo + diseinfo） */
@Data
@Accessors(chain = true)
public class Ip2402Input {

    private Ip2402DscgInfo dscginfo;

    private List<ChsDiseInfoRow> diseinfo;
}
