/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.diagnosticreport.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 按就诊创建或获取总检报告命令（DM-CLN-008 §5.1.1）。
 */
@Getter
@Setter
public class CreateForEncounterCommand {

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 租户主键。 */
    private Long tenantId;

    /** 机构主键。 */
    private Long orgId;

    /** 操作人。 */
    private Long operatorId;
}
