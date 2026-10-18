/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.gainloss.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.inventory.warehouse.gainloss.dto.GainLossLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 编辑药库损益单请求（IF-WHS-GL-05）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(
    description = "编辑药库损益单 IF-WHS-GL-05",
    requiredProperties = {"busNo", "purposeLocationId", "practitionerId", "lines"}
)
public class UpdateGainLossRequest {

    @NotBlank(message = "busNo 不能为空")
    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    @NotNull(message = "purposeLocationId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 损益药库 purposeLocationId。 */
    @Schema(description = "损益药库 purposeLocationId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long purposeLocationId;

    @NotNull(message = "经手人不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 经手人 practitionerId。 */
    @Schema(description = "经手人 practitionerId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long practitionerId;

    /** 供应类别 categoryEnum。 */
    @Schema(description = "供应类别 categoryEnum")
    private String categoryEnum;

    @NotEmpty(message = "明细不能为空")
    /** 明细行 lines。 */
    @Schema(description = "明细行 lines", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<GainLossLineDto> lines;
}
