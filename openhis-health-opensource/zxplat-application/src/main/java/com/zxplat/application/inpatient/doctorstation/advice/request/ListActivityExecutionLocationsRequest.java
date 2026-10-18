/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.advice.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * IF-IP-DOR-19：按诊疗活动定义查询可执行科室/地点规则（须与住院就诊、患者门控一致）。
 */
@Data
/** 医生站-按诊疗定义查询执行科室规则。 */
@Schema(description = "医生站-按诊疗定义查询执行科室规则")
public class ListActivityExecutionLocationsRequest {

    @Schema(description = "就诊主键")
    private Long encounterId;

    @Schema(description = "患者主键（须与就诊绑定一致）")
    /** 患者主键（须与就诊绑定一致）。 */
    private Long patientId;

    /** 诊疗活动定义主键。 */
    @Schema(description = "诊疗活动定义主键")
    private Long activityDefinitionId;
}
