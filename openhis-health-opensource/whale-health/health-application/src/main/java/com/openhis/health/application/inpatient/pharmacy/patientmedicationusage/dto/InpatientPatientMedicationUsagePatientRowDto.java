/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院患者药品使用-患者候选行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院患者药品使用-患者候选行")
public class InpatientPatientMedicationUsagePatientRowDto {

    /**
     * 就诊主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键")
    private Long encounterId;

    /**
     * 患者主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者主键")
    private Long patientId;

    /**
     * 患者姓名。
     */
    @Schema(description = "患者姓名")
    private String patientName;

    /**
     * 住院号。
     */
    @Schema(description = "住院号")
    private String inpatientNo;

    /**
     * 病区名称。
     */
    @Schema(description = "病区名称")
    private String wardName;

    /**
     * 床号展示名。
     */
    @Schema(description = "床号")
    private String bedName;

    /**
     * 就诊状态 code（{@code EncounterStatus.getCode()}）。
     */
    @Schema(description = "就诊状态 code")
    private String statusEnum;
}
