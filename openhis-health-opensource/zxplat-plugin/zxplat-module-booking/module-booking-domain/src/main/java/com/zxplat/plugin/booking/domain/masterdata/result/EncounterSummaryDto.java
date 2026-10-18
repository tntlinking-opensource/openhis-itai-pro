/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.masterdata.result;

import lombok.Getter;
import lombok.Setter;

/**
 * 就诊摘要（跨模块读 Encounter 头信息）。
 */
@Getter
@Setter
public class EncounterSummaryDto {

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 科室/机构主键。 */
    private Long organizationId;

    /** 就诊业务编号。 */
    private String busNo;
}
