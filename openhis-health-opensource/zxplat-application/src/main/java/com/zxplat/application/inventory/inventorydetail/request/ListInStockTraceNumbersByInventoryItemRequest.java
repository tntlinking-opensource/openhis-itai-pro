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

/**
 * 现库行本库仍在库追溯码查询入参（IF-INV-DTL-13 · IF-TRC-22）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(
    description = "现库行本库仍在库追溯码 IF-INV-DTL-13",
    requiredProperties = {"itemId"}
)
public class ListInStockTraceNumbersByInventoryItemRequest {

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库存行库房主键；本库查询必填，hospitalWide 浏览可空")
    /** 库存行库房主键；本库查询必填，hospitalWide 浏览可空。 */
    private Long locationId;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药品定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
    /** 药品定义主键。 */
    private Long itemId;

    @Schema(description = "批号；本库/库取回落必填，hospitalWide 按药品全批浏览时忽略")
    /** 批号；本库/库取回落必填，hospitalWide 按药品全批浏览时忽略。 */
    private String lotNumber;

    @Schema(description = "物品表名；默认 med_medication_definition，本期仅支持药品")
    /** 物品表名；默认 med_medication_definition，本期仅支持药品。 */
    private String itemTable;

    /**
     * 本库无在库码时是否回落全院同品同批在院码（发药库取场景）。
     *
     * <p>{@code true}：本库列表为空才返回主档在院码；本库有码则只用本库（禁止与全院混取）。
     * {@code false}/空：仅本库。与 {@link #hospitalWide} 同时为 true 时以 {@code hospitalWide} 为准。</p>
     */
    @Schema(description = "本库无码时回落全院同品同批在院码")
    /** 本库无码时回落全院同品同批在院码。 */
    private Boolean hospitalWideFallbackWhenEmpty;

    /**
     * 是否按药品查全院在院码（扫码弹窗：药品 → 批号列表 → 码）。
     *
     * <p>{@code true}：跳过本库子表与入参批号过滤，读主档该药品全部在院且拆零剩余 &gt; 0 的码；
     * 出参带 {@code lotNumber}/{@code remainingSplitQuantity}/药典拆零单位，不回填所在库位。
     * 库房、批号均可空。</p>
     */
    @Schema(description = "按药品查全院在院码（含全部批号）；优先于 hospitalWideFallbackWhenEmpty")
    /** 按药品查全院在院码（含全部批号）；优先于 hospitalWideFallbackWhenEmpty。 */
    private Boolean hospitalWide;
}
