/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.workinjury.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/** 工伤就诊上下文查询入参。 */
@Data
@Accessors(chain = true)
public class WorkInjuryEncounterContextQueryExtensionIn {

    private Long encounterId;
}
