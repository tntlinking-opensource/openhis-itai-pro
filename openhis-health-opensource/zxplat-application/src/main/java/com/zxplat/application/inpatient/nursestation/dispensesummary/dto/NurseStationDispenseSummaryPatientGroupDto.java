/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.dispensesummary.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 待汇总明细 · 按就诊分组（IF-IP-SUM-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "待汇总明细就诊分组")
public class NurseStationDispenseSummaryPatientGroupDto {

    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    @Schema(description = "患者主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 床号展示。 */
    @Schema(description = "床号展示")
    private String bedDisplay;

    /** 医嘱维度行。 */
    @Schema(description = "医嘱维度行")
    private List<NurseStationDispenseSummaryOrderRowDto> orderRows;
}
