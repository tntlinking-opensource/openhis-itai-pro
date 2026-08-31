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
     * 就诊范围 OUTPATIENT/INPATIENT；空则默认门诊，用于刷新默认登录科室下拉口径。
     */
    @Schema(description = "就诊范围 OUTPATIENT/INPATIENT")
    private String encounterScope;

    /**
     * 默认登录科室主键（可选；{@code Practitioner.org_id}）。
     *
     * <p>已选时护士下拉仅返回该科人员；未选返回全院护士岗。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "默认登录科室主键（可选；Practitioner.org_id）")
    private Long organizationId;
}
