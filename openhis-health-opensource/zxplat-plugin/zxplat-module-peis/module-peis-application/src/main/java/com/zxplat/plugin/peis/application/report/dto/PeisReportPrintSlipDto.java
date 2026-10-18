/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 体检总检报告打印 slip（与 peis-report-print 模板 PrintData 契约一致）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体检总检报告打印 slip")
public class PeisReportPrintSlipDto {

    /** 机构全称（模板抬头 hospitalName）。 */
    @Schema(description = "机构名称")
    private String hospitalName;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 性别中文展示（男/女等）。 */
    @Schema(description = "性别展示")
    private String genderEnumText;

    /** 年龄展示（如 25岁）。 */
    @Schema(description = "年龄展示")
    private String ageText;

    /** 体检流水号（peis_encounter_ext.bus_no）。 */
    @Schema(description = "体检流水号")
    private String busNo;

    /** 总检定稿时间展示（yyyy-MM-dd HH:mm）。 */
    @Schema(description = "定稿时间展示")
    private String checkDateText;

    /** 总检综述文本。 */
    @Schema(description = "综述")
    private String summaryText;

    /** 总检结论文本。 */
    @Schema(description = "结论")
    private String conclusionText;

    /** 总检报告主键 cln_diagnostic_report.id。 */
    @Schema(description = "总检报告ID")
    private Long diagnosticReportId;

    /** 就诊主键 adm_encounter.id。 */
    @Schema(description = "就诊ID")
    private Long encounterId;

    /** 患者主键 adm_patient.id。 */
    @Schema(description = "患者ID")
    private Long patientId;

    /** 打印场景码（固定 peis-report-print）。 */
    @Schema(description = "打印场景码")
    private String scenarioCode;

    /** 观察结果明细行（模板 observationLines 表格）。 */
    @Schema(description = "观察结果行")
    private List<ObservationLineDto> observationLines = new ArrayList<>();

    /**
     * 观察结果打印行（与模板 observationLines 列 field 一致）。
     */
    @Data
    @Accessors(chain = true)
    @Schema(description = "观察结果打印行")
    public static class ObservationLineDto {

        /** 观察项名称（观察定义 display）。 */
        @Schema(description = "项目名称")
        private String itemName;

        /** 结果值展示（优先 valueString，否则 valueQuantity）。 */
        @Schema(description = "结果")
        private String resultText;

        /** 单位展示（观察定义 unitCode）。 */
        @Schema(description = "单位")
        private String unitText;

        /** 参考范围展示（P1 可 enrich；当前可为空）。 */
        @Schema(description = "参考范围")
        private String refRangeText;
    }
}
