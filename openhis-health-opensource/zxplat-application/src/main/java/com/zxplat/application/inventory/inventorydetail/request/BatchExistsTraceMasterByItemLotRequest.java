/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.inventorydetail.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量判定主表品批是否已有追溯码（BR-TRC-020 · 有码批/无码批）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "批量判定品批是否有主档追溯码")
public class BatchExistsTraceMasterByItemLotRequest {

    /** 品批键列表；空则返回空结果。 */
    @Schema(description = "品批键列表")
    private List<ItemLotKey> keys;

    /**
     * 单个品批键。
     */
    @Getter
    @Setter
    @Accessors(chain = true)
    @Schema(description = "品批键")
    public static class ItemLotKey {

        /** 药品定义主键。 */
        @JsonSerialize(using = ToStringSerializer.class)
        @Schema(description = "药品定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
        private Long itemId;

        /** 批号。 */
        @Schema(description = "批号", requiredMode = Schema.RequiredMode.REQUIRED)
        private String lotNumber;

        /** 物品表名；默认药品表。 */
        @Schema(description = "物品表名；默认 med_medication_definition")
        private String itemTable;
    }
}
