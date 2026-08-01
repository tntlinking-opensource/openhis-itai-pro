/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.observation.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 作废临床观察实例命令（DM-CLN-007 §5.1.4）。
 */
@Getter
@Setter
public class CancelObservationCommand {

    /** 观察实例主键。 */
    private Long observationId;

    /** 作废原因。 */
    private String reason;
}
