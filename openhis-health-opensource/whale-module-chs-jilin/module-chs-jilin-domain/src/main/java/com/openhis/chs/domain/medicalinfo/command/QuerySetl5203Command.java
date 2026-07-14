/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.medicalinfo.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/** 【5203】结算信息查询 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class QuerySetl5203Command extends MedicalInfoQueryBaseCommand {

    private String mdtrtId;
    private String setlId;
}
