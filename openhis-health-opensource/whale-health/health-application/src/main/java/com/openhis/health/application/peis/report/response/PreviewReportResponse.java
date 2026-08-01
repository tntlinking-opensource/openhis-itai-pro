/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.report.response;

import com.openhis.health.application.peis.report.dto.PeisReportPrintSlipDto;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 预览报告响应（IF-PEIS-REP-02；内容预览 + 打印 slip 关键字段）。
 */
@Data
public class PreviewReportResponse {

    /** 就诊标识。 */
    private Long encounterId;

    /** 总检报告主键。 */
    private Long diagnosticReportId;

    /** 综述文本。 */
    private String summaryText;

    /** 结论文本。 */
    private String conclusionText;

    /** 预览提示。 */
    private String previewHint;

    /** 打印场景码（peis-report-print）。 */
    private String scenarioCode;

    /** 机构名称。 */
    private String hospitalName;

    /** 患者姓名。 */
    private String patientName;

    /** 性别展示。 */
    private String genderEnumText;

    /** 年龄展示。 */
    private String ageText;

    /** 体检流水号。 */
    private String busNo;

    /** 定稿时间展示。 */
    private String checkDateText;

    /** 观察结果行（与打印 slip 一致）。 */
    private List<PeisReportPrintSlipDto.ObservationLineDto> observationLines = new ArrayList<>();
}
