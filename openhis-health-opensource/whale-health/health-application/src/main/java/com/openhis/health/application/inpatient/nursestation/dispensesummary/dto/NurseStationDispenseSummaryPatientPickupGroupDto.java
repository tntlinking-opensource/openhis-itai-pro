/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.dispensesummary.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 摆药单查询 · 按就诊/患者分组（IF-IP-SUM-09）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "摆药单查询患者分组")
public class NurseStationDispenseSummaryPatientPickupGroupDto {

    /** 就诊主键。 */
    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
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

    /** 本患者下按 medReqId 合并后的药品行。 */
    @Schema(description = "药品行（同 medReqId 已合并数量）")
    private List<NurseStationDispenseSummaryPatientPickupLineDto> lines;
}
