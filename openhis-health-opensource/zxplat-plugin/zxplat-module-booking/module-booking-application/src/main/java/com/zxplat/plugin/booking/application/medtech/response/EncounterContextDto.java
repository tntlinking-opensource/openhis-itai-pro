/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.medtech.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 就诊上下文 DTO（医技预约）。
 */
@Getter
@Setter
public class EncounterContextDto {

    /** 就诊主键。 */
    private Long encounterId;

    /** 就诊流水号。 */
    private String encounterBusNo;

    /** 患者主键。 */
    private Long patientId;

    /** 患者姓名。 */
    private String patientName;

    /** 机构/科室主键。 */
    private Long organizationId;

    /** 机构/科室名称。 */
    private String organizationName;
}
