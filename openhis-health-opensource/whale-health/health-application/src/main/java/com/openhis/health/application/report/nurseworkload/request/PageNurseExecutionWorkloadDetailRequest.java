/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.nurseworkload.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 护士诊疗执行明细分页入参（IF-RPT-NWL-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "护士诊疗执行明细分页 IF-RPT-NWL-03")
public class PageNurseExecutionWorkloadDetailRequest {

    /** 就诊范围 OUTPATIENT/INPATIENT。 */
    @NotNull(message = "请选择门诊或住院")
    @Schema(description = "就诊范围 OUTPATIENT/INPATIENT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String encounterScope;

    /** 统计日起。 */
    @NotNull(message = "请选择查询开始日期")
    @Schema(description = "统计日起", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate dateFrom;

    /** 统计日止。 */
    @NotNull(message = "请选择查询结束日期")
    @Schema(description = "统计日止", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate dateTo;

    /** 护士执业者主键（可选）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "护士执业者主键（可选）")
    private Long practitionerId;

    /** 页码（从 1 开始）。 */
    @Schema(description = "页码（从 1 开始）")
    private Integer pageNo;

    /** 页大小（默认 20，上限 1000）。 */
    @Schema(description = "页大小（默认 20，上限 1000）")
    private Integer pageSize;

    /** 开立科室主键（可选；金额二次收窄）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "开立科室主键（可选；金额二次收窄）")
    private Long requestingOrgId;

    /**
     * 默认登录科室主键（可选；{@code Practitioner.org_id}）。
     *
     * <p>未传单护士时按该科下全部护士收窄。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "默认登录科室主键（可选；Practitioner.org_id）")
    private Long organizationId;
}
