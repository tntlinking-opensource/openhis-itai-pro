/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 当前就诊电子处方列表查询。 */
@Data
@Accessors(chain = true)
public class ElepEncounterListQuery {

    /** 当前门诊就诊主键。 */
    private Long encounterId;
}
