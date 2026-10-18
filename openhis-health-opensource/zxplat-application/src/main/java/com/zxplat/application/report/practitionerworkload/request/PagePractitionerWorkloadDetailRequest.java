/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.practitionerworkload.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 科室/医生工作量明细分页入参（IF-RPT-WKL-03）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "科室医生工作量明细分页 IF-RPT-WKL-03")
public class PagePractitionerWorkloadDetailRequest extends AbstractHealthPageRequest {

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
