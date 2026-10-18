/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.orchestration.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 团检收费成功后建单命令（AS-PE-FIN-002）。
 */
@Getter
@Setter
public class RegisterGroupAfterPaidCommand {

    private Long patientId;
    private Long packageActivityId;
    private Long orgId;
    private Long operatorId;
    private Long companyId;
    private Long contractId;
    private Long groupBatchId;
    /** freshman / annual / staff / other；空则推断 */
    private String examKind;
}
