/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.medicalinfo.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/** 【5206】人员累计信息查询 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class QueryCum5206Command extends MedicalInfoQueryBaseCommand {

    /** yyyyMM */
    private String cumYm;
}
