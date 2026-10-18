/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.transferout.dto;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药库调出单写模型明细行（IF-WHS-TR-04/05）。
 *
 * <p>数量与 {@link #unitCode} 为行上业务口径；同意链换算为拆零数量由应用服务按产品读模型完成。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "药库调出单明细行（写）")
public class TransferOutLineDto {

    /** 物品来源表名。 */
    @Schema(description = "物品来源表名")
    private String itemTable;

    /** 物品定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "物品定义主键")
    private Long itemId;

    /** 数量。 */
    @Schema(description = "数量")
    private BigDecimal itemQuantity;

    /** 计量单位编码。 */
    @Schema(description = "计量单位编码")
    private String unitCode;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /**
     * 选品返回的库存行主键（{@code wkf_inventory_item.id}）；若传入，则草稿可用量门禁优先按该行精确比对，弱化批号字符串形态差异。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库存项主键（选品 inventoryItemId，可选）")
    /** 库存项主键（选品 inventoryItemId，可选）。 */
    private Long inventoryItemId;

    /** 追溯码。 */
    @Schema(description = "追溯码")
    private String traceNo;

    /** 单价。 */
    @Schema(description = "单价")
    private BigDecimal price;

    /** 行总价。 */
    @Schema(description = "行总价")
    private BigDecimal totalPrice;

    /** 行备注。 */
    @Schema(description = "行备注")
    private String remake;

    /** 业务发生时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "业务发生时间")
    private LocalDateTime occurrenceTime;
}
