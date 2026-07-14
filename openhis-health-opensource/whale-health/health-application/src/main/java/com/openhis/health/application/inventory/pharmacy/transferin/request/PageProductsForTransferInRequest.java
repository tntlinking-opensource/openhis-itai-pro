/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.transferin.request;

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
 * 药房调入选品分页请求（IF-PHA-TI-11）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(
    description = "药房调入选品分页 IF-PHA-TI-11",
    requiredProperties = {"sourceLocationId", "productTableName", "pageNo", "pageSize"}
)
public class PageProductsForTransferInRequest extends AbstractHealthPageRequest {

    @NotNull(message = "sourceLocationId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 来源药库主键。 */
    @Schema(description = "来源药库主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sourceLocationId;

    @NotBlank(message = "productTableName 不能为空")
    /** 物品物理表 med_medication_definition / adm_device_definition。 */
    @Schema(description = "物品物理表 med_medication_definition / adm_device_definition", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productTableName;
}
