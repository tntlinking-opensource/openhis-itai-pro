/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.medicalinfo.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/** 【5202】诊断信息查询 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class QueryDise5202Command extends MedicalInfoQueryBaseCommand {

    private String mdtrtId;
}
