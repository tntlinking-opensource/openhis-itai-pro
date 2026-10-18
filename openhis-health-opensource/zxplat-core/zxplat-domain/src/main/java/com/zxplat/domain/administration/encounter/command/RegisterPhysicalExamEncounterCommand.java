/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 创建体检就诊（EncounterClass.PE）命令。
 */
@Getter
@Setter
public class RegisterPhysicalExamEncounterCommand {

    /** 患者主键。 */
    private Long patientId;

    /** 体检中心机构主键。 */
    private Long organizationId;

    /** 登记员 practitioner 主键。 */
    private Long registrarId;
}
