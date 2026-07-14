/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.inventorydetail.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 现库行本库仍在库追溯码查询入参（IF-INV-DTL-13 · IF-TRC-22）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(
    description = "现库行本库仍在库追溯码 IF-INV-DTL-13",
    requiredProperties = {"locationId", "itemId", "lotNumber"}
)
public class ListInStockTraceNumbersByInventoryItemRequest {

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库存行库房主键", requiredMode = Schema.RequiredMode.REQUIRED)
    /** 库存行库房主键。 */
    private Long locationId;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药品定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
    /** 药品定义主键。 */
    private Long itemId;

    @Schema(description = "批号", requiredMode = Schema.RequiredMode.REQUIRED)
    /** 批号。 */
    private String lotNumber;

    @Schema(description = "物品表名；默认 med_medication_definition，本期仅支持药品")
    /** 物品表名；默认 med_medication_definition，本期仅支持药品。 */
    private String itemTable;
}
