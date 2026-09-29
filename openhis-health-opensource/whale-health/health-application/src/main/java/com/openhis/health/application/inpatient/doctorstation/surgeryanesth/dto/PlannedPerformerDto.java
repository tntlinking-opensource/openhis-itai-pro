/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.surgeryanesth.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 拟手术/麻醉参与人员（AS-IP-DOR-012）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "拟手术参与人员")
public class PlannedPerformerDto {

    /** 角色码（ClinicalProcedureConstants）。 */
    @Schema(description = "角色码：SURGEON/FIRST_ASSISTANT/SECOND_ASSISTANT/NURSE/ANESTHETIST")
    private String roleCode;

    /** 医务人员主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "医务人员主键")
    private Long practitionerId;

    /** 展示姓名。 */
    @Schema(description = "医务人员姓名")
    private String practitionerName;
}
