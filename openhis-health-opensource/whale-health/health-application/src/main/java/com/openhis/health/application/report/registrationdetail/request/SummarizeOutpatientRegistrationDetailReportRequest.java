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
 * 门诊挂号明细报表汇总查询请求（IF-RPT-ORG-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊挂号明细报表汇总查询请求")
public class SummarizeOutpatientRegistrationDetailReportRequest {

    /** 挂号业务日起（yyyy-MM-dd，闭区间；BR-RPT-ORG-001）。 */
    @Schema(description = "挂号业务日起（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dateFrom;

    /** 挂号业务日止（yyyy-MM-dd，闭区间）。 */
    @Schema(description = "挂号业务日止（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dateTo;

    /**
     * 汇总视角：{@code DEPARTMENT}（按科室）或 {@code DOCTOR}（按医生）；缺省按科室。
     */
    @Schema(description = "汇总视角 DEPARTMENT/DOCTOR")
    private String summaryView;

    /** 挂号科室主键；空表示门诊挂号科室全量。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "挂号科室主键")
    private Long organizationId;

    /** 接诊医生主键（活动 ADMITTER）；空表示不按接诊医生收窄（BR-RPT-ORG-002 分支）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "接诊医生主键（活动 ADMITTER）")
    private Long practitionerId;

    /** 患者姓名/门诊号关键字；空不收窄。 */
    @Schema(description = "患者或门诊号关键字")
    private String keyword;
}

