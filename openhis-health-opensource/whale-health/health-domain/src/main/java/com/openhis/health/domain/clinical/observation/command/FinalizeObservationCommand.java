/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.observation.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 确认临床观察实例命令（DM-CLN-007 §5.1.3）。
 */
@Getter
@Setter
public class FinalizeObservationCommand {

    /** 观察实例主键。 */
    private Long observationId;

    /** 操作人主键。 */
    private Long operatorId;
}
