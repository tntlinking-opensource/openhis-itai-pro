/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.traceassignment.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-TRC-01：待追溯分配患者队列行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "待追溯分配患者队列行")
public class PatientTraceAssignmentQueueRowDto {

    /** 就诊主键。 */
    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 住院号（就诊 busNo）。 */
    @Schema(description = "住院号")
    private String encounterBusNo;

    /** 患者主键。 */
    @Schema(description = "患者主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 床号。 */
    @Schema(description = "床号")
    private String bedName;

    /** 待分配汇总组数。 */
    @Schema(description = "待分配汇总组数")
    private Integer pendingGroupCount;
}
