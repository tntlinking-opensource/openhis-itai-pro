/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.medtech.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 就诊检索请求（医技预约）。
 */
@Getter
@Setter
public class SearchEncounterRequest {

    /** 机构/科室主键。 */
    @NotNull
    private Long organizationId;

    /** 检索关键字（患者姓名等）。 */
    private String keyword;

    /** 就诊流水号。 */
    private String encounterBusNo;

    /** 患者主键。 */
    private Long patientId;
}
