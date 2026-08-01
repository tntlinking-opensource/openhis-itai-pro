/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.inventorydetail.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量判定主表品批是否已有追溯码出参（BR-TRC-020）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "批量判定品批是否有主档追溯码出参")
public class BatchExistsTraceMasterByItemLotResponse {

    /** 与请求键对应的判定结果。 */
    @Schema(description = "判定结果列表")
    private List<ItemLotExistsRow> items = new ArrayList<>();

    /**
     * 单行判定结果。
     */
    @Getter
    @Setter
    @Accessors(chain = true)
    @Schema(description = "品批有码判定行")
    public static class ItemLotExistsRow {

        /** 药品定义主键。 */
        @JsonSerialize(using = ToStringSerializer.class)
        @Schema(description = "药品定义主键")
        private Long itemId;

        /** 批号。 */
        @Schema(description = "批号")
        private String lotNumber;

        /** 物品表名。 */
        @Schema(description = "物品表名")
        private String itemTable;

        /**
         * 主表该品批是否已有追溯码（有码批=true）。
         */
        @Schema(description = "主表该品批是否已有追溯码")
        private Boolean lotHasTraceMaster;
    }
}
