/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 体检总检完成后结束就诊命令（AS-PEIS-MAS-001 MAS-04 / BR-MAS-05）。
 */
@Getter
@Setter
public class FinishEncounterCommand {

    /** 就诊主键。 */
    private Long encounterId;

    /** 操作人。 */
    private Long operatorId;
}
