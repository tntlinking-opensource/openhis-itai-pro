/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.masterdata.result;

import lombok.Getter;
import lombok.Setter;

/**
 * 就诊检索结果（医技预约上下文）。
 */
@Getter
@Setter
public class EncounterSearchResultDto {

    /** 就诊主键。 */
    private Long encounterId;

    /** 就诊业务编号。 */
    private String encounterBusNo;

    /** 患者主键。 */
    private Long patientId;

    /** 患者姓名。 */
    private String patientName;

    /** 科室/机构主键。 */
    private Long organizationId;

    /** 科室名称。 */
    private String organizationName;
}
