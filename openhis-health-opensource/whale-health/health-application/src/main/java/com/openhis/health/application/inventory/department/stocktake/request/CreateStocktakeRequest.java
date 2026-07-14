/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.stocktake.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inventory.department.stocktake.dto.StocktakeLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 新建科室药房盘点单入参（IF-DEPT-ST-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "新建盘点单 IF-DEPT-ST-04")
public class CreateStocktakeRequest {

    /** 盘点科室药房 purposeLocationId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "盘点科室药房 purposeLocationId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long purposeLocationId;

    /** 经手人医务人员主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 经手人 practitionerId。 */
    @Schema(description = "经手人 practitionerId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long practitionerId;

    /** 供应类别 categoryEnum。 */
    @Schema(description = "供应类别 categoryEnum")
    private String categoryEnum;

    /** 明细行 lines。 */
    @Schema(description = "明细行 lines", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<StocktakeLineDto> lines;
}
