/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.history.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ListPatientExamHistoryRequest {
    private Long patientId;
    private String studentNo;
    private String employeeNo;
    /** 可选过滤：freshman/annual/staff/other */
    private List<String> examKinds;
    /** 仅返回总检 final 的行 */
    private Boolean onlyFinal;
}
