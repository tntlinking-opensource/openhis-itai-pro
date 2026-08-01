/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.diagnosticreport.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 总检定稿命令（DM-CLN-008 §5.1.3）。
 */
@Getter
@Setter
public class FinalizeMasterCommand {

    /** 就诊主键。 */
    private Long encounterId;

    /** 操作人。 */
    private Long operatorId;
}
