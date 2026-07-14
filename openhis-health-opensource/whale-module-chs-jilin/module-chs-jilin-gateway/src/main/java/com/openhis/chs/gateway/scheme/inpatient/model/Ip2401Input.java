/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.inpatient.model;

import com.openhis.chs.gateway.scheme.common.model.ChsDiseInfoRow;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 【2401】入院办理 input（mdtrtinfo + diseinfo） */
@Data
@Accessors(chain = true)
public class Ip2401Input {

    private Ip2401MdtrtInfo mdtrtinfo;

    private List<ChsDiseInfoRow> diseinfo;
}
