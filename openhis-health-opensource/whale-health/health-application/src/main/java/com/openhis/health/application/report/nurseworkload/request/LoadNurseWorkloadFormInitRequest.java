/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.nurseworkload.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 护士工作量统计页初始化入参（IF-RPT-NWL-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "护士工作量统计页初始化 IF-RPT-NWL-01")
public class LoadNurseWorkloadFormInitRequest {

    /**
     * 就诊范围 OUTPATIENT/INPATIENT；空则默认门诊。工作科室下拉不随就诊域变化。
     */
    @Schema(description = "就诊范围 OUTPATIENT/INPATIENT")
    private String encounterScope;

    /**
     * 工作科室主键（可选；岗位 {@code adm_pr_assign.org_id}）。
     *
     * <p>已选时人员下拉仅返回挂了该工作科室的护士/医技；未选返回这两类角色全员。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "工作科室主键（可选；岗位 adm_pr_assign.org_id）")
    private Long organizationId;
}
