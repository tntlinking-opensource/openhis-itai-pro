/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 当前就诊电子处方列表查询。 */
@Data
@Accessors(chain = true)
public class ElepEncounterListQuery {

    private Long encounterId;
}
