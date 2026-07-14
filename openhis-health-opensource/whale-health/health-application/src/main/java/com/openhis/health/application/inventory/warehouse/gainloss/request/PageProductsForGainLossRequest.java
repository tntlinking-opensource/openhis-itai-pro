/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.gainloss.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 药库损益选品分页请求（IF-WHS-GL-11）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(
    description = "药库损益选品分页 IF-WHS-GL-11",
    requiredProperties = {"purposeLocationId", "productTableName", "pageNo", "pageSize"}
)
public class PageProductsForGainLossRequest extends AbstractHealthPageRequest {

    @NotNull(message = "purposeLocationId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 损益药库 purposeLocationId。 */
    @Schema(description = "损益药库 purposeLocationId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long purposeLocationId;

    @NotBlank(message = "productTableName 不能为空")
    /** 物品物理表 med_medication_definition / adm_device_definition。 */
    @Schema(description = "物品物理表 med_medication_definition / adm_device_definition", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productTableName;
}
