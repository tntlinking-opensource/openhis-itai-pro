/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.inventorydetail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 库存行本库仍在库追溯码弹窗行（IF-INV-DTL-13）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "库存行本库仍在库追溯码行")
public class InventoryItemTraceNumberRowDto {

    @Schema(description = "追溯码；可跳转追溯码查询报表")
    /** 追溯码；可跳转追溯码查询报表。 */
    private String traceNo;

    @Schema(description = "批号（主档）")
    /** 批号（主档）；全院按药品浏览时供前端按批分组。 */
    private String lotNumber;

    @Schema(description = "本库净拆零量")
    /** 本库净拆零量（本库查询）；全院浏览场景可空。 */
    private BigDecimal locationNetSplitQuantity;

    @Schema(description = "主档拆零剩余量")
    /** 主档拆零剩余量（单位见 splitUnitCode）。 */
    private BigDecimal remainingSplitQuantity;

    @Schema(description = "拆零单位编码（药典）")
    /** 拆零单位编码（药典 {@code split_unit_code}）；remainingSplitQuantity 的单位。 */
    private String splitUnitCode;

    @Schema(description = "拆零单位展示名")
    /** 拆零单位展示名（术语 unit_code 转译）。 */
    private String splitUnitCodeLabel;

    @Schema(description = "追溯码单位编码（主档）")
    /** 追溯码单位编码（主档）。 */
    private String traceUnitCode;

    @Schema(description = "追溯码单位展示名")
    /** 追溯码单位展示名（术语 unit_code 转译）。 */
    private String traceUnitCodeLabel;

    @Schema(description = "主档贴码比例快照（1 码对应拆零量）")
    /** 主档贴码比例快照（1 码对应拆零量）。 */
    private BigDecimal traceUnitRatio;

    @Schema(description = "全院在院状态 code")
    /** 全院在院状态 code。 */
    private String inHospitalStatusEnum;

    @Schema(description = "全院在院状态展示名")
    /** 全院在院状态展示名。 */
    private String inHospitalStatusLabel;
}
