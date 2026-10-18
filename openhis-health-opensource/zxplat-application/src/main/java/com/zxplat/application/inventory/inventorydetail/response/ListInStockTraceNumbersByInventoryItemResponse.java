/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.inventorydetail.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.inventory.inventorydetail.dto.InventoryItemTraceNumberRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 现库行本库仍在库追溯码查询出参（IF-INV-DTL-13 · IF-TRC-22）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "现库行本库仍在库追溯码 IF-INV-DTL-13")
public class ListInStockTraceNumbersByInventoryItemResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库存行库房主键")
    /** 库存行库房主键。 */
    private Long locationId;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药品定义主键")
    /** 药品定义主键。 */
    private Long itemId;

    @Schema(description = "批号")
    /** 批号。 */
    private String lotNumber;

    @Schema(description = "本库净量大于 0 的追溯码列表")
    /** 本库净量大于 0 的追溯码列表。 */
    private List<InventoryItemTraceNumberRowDto> items;
}
