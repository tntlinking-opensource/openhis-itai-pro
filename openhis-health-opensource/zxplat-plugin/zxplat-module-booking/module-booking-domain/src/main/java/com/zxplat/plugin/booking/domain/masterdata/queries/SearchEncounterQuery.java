/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.masterdata.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 就诊检索条件（医技预约）。
 */
@Getter
@Setter
public class SearchEncounterQuery {

    private Long organizationId;
    private String keyword;
    private String encounterBusNo;
    private Long patientId;
}
