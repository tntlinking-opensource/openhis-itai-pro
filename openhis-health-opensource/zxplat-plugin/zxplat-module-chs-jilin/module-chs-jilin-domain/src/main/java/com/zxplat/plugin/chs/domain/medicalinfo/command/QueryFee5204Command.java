/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.medicalinfo.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/** 【5204】费用明细查询 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class QueryFee5204Command extends MedicalInfoQueryBaseCommand {

    private String mdtrtId;
    private String setlId;
}
