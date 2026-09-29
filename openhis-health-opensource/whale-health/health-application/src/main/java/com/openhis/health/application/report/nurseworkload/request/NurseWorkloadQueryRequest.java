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
 * 护士工作量汇总查询入参（IF-RPT-NWL-02/04；BR-RPT-NWL-018 与明细筛选一致）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "护士工作量汇总查询 IF-RPT-NWL-02/04")
public class NurseWorkloadQueryRequest {

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

    /** 护士执业者主键（可选；单人明细优先于科室集合）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "护士执业者主键（可选）")
    private Long practitionerId;

    /**
     * 开立科室主键（可选；仅金额按 {@code ChargeItem.requesting_org_id} 二次收窄时使用）。
     *
     * <p>科室工作量统计请传 {@link #organizationId}（工作科室），勿与本字段混用。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "开立科室主键（可选；金额二次收窄）")
    private Long requestingOrgId;

    /**
     * 工作科室主键（可选；岗位 {@code adm_pr_assign.org_id}）。
     *
     * <p>已选时统计该科下护士/医技岗位人员工作量合计与卡片；未选=当前就诊域全院。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "工作科室主键（可选；岗位 adm_pr_assign.org_id）")
    private Long organizationId;
}
