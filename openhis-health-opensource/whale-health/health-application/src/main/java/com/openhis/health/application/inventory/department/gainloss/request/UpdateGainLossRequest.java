/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.gainloss.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inventory.department.gainloss.dto.GainLossLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 编辑科室药房损益单入参（IF-DEPT-GL-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "编辑科室药房损益单")
public class UpdateGainLossRequest {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /** 损益科室药房 purposeLocationId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "损益科室药房 purposeLocationId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long purposeLocationId;

    /** 经手人 practitionerId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "经手人 practitionerId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long practitionerId;

    /** 供应类别 categoryEnum。 */
    @Schema(description = "供应类别 categoryEnum")
    private String categoryEnum;

    /** 明细行 lines。 */
    @Schema(description = "明细行 lines", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<GainLossLineDto> lines;
}
