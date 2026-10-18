/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.recheck.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 创建复查任务命令（AS-PEIS-REC-001）。
 */
@Getter
@Setter
public class CreateRecheckTaskCommand {

    /** 原体检就诊标识。 */
    private Long encounterId;

    /** 患者标识。 */
    private Long patientId;

    /** 机构标识。 */
    private Long orgId;

    /** 租户标识。 */
    private Long tenantId;

    /** 复查原因。 */
    private String reason;

    /** 操作人标识。 */
    private Long operatorId;
}
