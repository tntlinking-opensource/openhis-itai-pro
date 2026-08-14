/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.registrationdetail.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊挂号明细报表导出请求（IF-RPT-ORG-04；仅当前 Tab）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊挂号明细报表导出请求")
public class ExportOutpatientRegistrationDetailReportRequest {

    /** 挂号业务日起（yyyy-MM-dd）。 */
    @Schema(description = "挂号业务日起（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dateFrom;

    /** 挂号业务日止（yyyy-MM-dd）。 */
    @Schema(description = "挂号业务日止（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dateTo;

    /**
     * 导出 Tab：{@code SUMMARY} 或 {@code DETAIL}（BR-RPT-ORG-005）。
     */
    @Schema(description = "导出 Tab SUMMARY/DETAIL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String presentationMode;

    /** 汇总视角（仅 SUMMARY Tab 生效）：DEPARTMENT / DOCTOR。 */
    @Schema(description = "汇总视角 DEPARTMENT/DOCTOR")
    private String summaryView;

    /** 挂号科室主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "挂号科室主键")
    private Long organizationId;

    /** 接诊医生主键（活动 ADMITTER）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "接诊医生主键（活动 ADMITTER）")
    private Long practitionerId;

    /** 患者姓名/门诊号关键字。 */
    @Schema(description = "患者或门诊号关键字")
    private String keyword;
}

