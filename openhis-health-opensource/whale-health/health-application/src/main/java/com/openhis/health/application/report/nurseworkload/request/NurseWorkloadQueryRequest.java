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

    /** 护士执业者主键（可选）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "护士执业者主键（可选）")
    private Long practitionerId;
}
