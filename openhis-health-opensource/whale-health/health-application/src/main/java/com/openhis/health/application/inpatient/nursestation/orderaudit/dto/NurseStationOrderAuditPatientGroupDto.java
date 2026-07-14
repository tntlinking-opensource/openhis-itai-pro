/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderaudit.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 按就诊分组的医嘱校对列表（IF-IP-AUD-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "按就诊分组的医嘱校对列表")
public class NurseStationOrderAuditPatientGroupDto {

    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 就诊主键。 */
    private Long encounterId;

    /** 该就诊下医嘱行（已排序）。 */
    @Schema(description = "该就诊下医嘱行（已排序）")
    private List<NurseStationOrderAuditRowDto> orders;
}
