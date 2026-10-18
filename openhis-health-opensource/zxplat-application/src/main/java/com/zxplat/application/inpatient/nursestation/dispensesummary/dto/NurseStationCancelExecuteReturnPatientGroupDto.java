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

import java.io.Serializable;
import java.util.List;

/**
 * 取消执行退药 · 按就诊（单患者）分组（IF-IP-SUM-10）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "取消执行退药就诊分组")
public class NurseStationCancelExecuteReturnPatientGroupDto implements Serializable {

    private static final long serialVersionUID = 1L;

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

    /** 该患者取消执行产生的退药行。 */
    @Schema(description = "退药明细行")
    private List<NurseStationCancelExecuteReturnLineDto> returnLines;
}
