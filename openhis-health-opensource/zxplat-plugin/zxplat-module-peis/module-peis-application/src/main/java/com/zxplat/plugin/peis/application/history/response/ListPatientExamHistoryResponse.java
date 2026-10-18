/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.history.response;

import com.zxplat.plugin.peis.application.history.dto.ExamHistoryRowDto;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ListPatientExamHistoryResponse {
    private Long patientId;
    private String studentNo;
    private String employeeNo;
    /** STUDENT / STAFF / 空 */
    private String subjectType;
    private String subjectName;
    private List<ExamHistoryRowDto> rows = new ArrayList<>();
}
