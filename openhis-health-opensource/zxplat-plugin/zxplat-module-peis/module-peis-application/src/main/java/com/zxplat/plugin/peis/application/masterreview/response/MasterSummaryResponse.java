/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.masterreview.response;

import com.zxplat.plugin.peis.application.masterreview.dto.DeptSummarySectionDto;
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

    /** 患者姓名。 */
    private String patientName;

    /** 体检流水号。 */
    private String busNo;

    /** 患者年龄（岁）。 */
    private Integer patientAge;

    /** 性别编码。 */
    private String genderEnum;

    /** 团检标志。 */
    private Boolean groupFlag;

    /** 分科完成比（如 5/5）。 */
    private String deptCompleteRatio;

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
