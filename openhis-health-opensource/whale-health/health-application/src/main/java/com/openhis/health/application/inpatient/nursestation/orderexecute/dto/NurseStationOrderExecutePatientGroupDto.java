/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderexecute.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 按就诊分组的医嘱执行列表（IF-IP-EXE-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "按就诊分组的医嘱执行列表")
public class NurseStationOrderExecutePatientGroupDto {

    /** 就诊主键。 */
    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 组内医嘱行。 */
    @Schema(description = "组内医嘱行")
    private List<NurseStationOrderExecuteRowDto> orders;
}
