/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.report.response;

import com.zxplat.plugin.peis.application.report.dto.GradReportRowDto;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ListGradReportsResponse {
    private Long patientId;
    private String studentNo;
    private List<GradReportRowDto> rows = new ArrayList<>();
}
