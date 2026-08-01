/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.masterreview.response;

import com.openhis.health.application.peis.masterreview.dto.DeptSummarySectionDto;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 总检汇总响应（IF-PEIS-MAS-02）。
 */
@Data
public class MasterSummaryResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 当前报告主键（可空）。 */
    private Long diagnosticReportId;

    /** 报告状态。 */
    private String reportStatusEnum;

    /** 已存综述。 */
    private String summaryText;

    /** 已存结论。 */
    private String conclusionText;

    /** 分科汇总段。 */
    private List<DeptSummarySectionDto> deptSections = new ArrayList<>();
}
