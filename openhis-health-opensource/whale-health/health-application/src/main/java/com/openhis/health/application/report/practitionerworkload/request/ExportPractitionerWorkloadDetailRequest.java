/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.practitionerworkload.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 导出科室/医生工作量明细入参（IF-RPT-WKL-04）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "导出科室医生工作量明细 IF-RPT-WKL-04")
public class ExportPractitionerWorkloadDetailRequest {

    /** 就诊范围。 */
    @NotBlank(message = "请选择门诊或住院")
    @Schema(description = "就诊范围", requiredMode = Schema.RequiredMode.REQUIRED)
    private String encounterScope;

    /** 查询日起。 */
    @NotBlank(message = "请选择查询开始日期")
    @Schema(description = "查询日起", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dateFrom;

    /** 查询日止。 */
    @NotBlank(message = "请选择查询结束日期")
    @Schema(description = "查询日止", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dateTo;

    /** 开立科室主键（可选；{@code ChargeItem.requesting_org_id}）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "开立科室主键（可选）")
    private Long requestingOrgId;

    /** 科室主键（遗留；报表中心请用 {@link #requestingOrgId}）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "科室主键（遗留；请用 requestingOrgId）")
    private Long organizationId;

    /** 医生主键（可选）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "医生主键（可选）")
    private Long practitionerId;
}
