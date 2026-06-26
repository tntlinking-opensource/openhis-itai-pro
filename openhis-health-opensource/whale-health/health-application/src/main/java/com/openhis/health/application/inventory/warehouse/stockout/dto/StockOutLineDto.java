/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inventory.warehouse.stockout.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 *
 * <p>数量与 {@link #unitCode} 为行上业务口径；同意链换算为拆零数量由应用服务按产品读模型完成。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
/** 药库出库单明细行。 */
@Schema(description = "药库出库单明细行（写）")
public class StockOutLineDto {

    @Schema(description = "物品来源表名")
    private String itemTable;

    /** 物品定义主键，与 {@link #itemTable} 组成复合引用。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 物品定义主键。 */
    @Schema(description = "物品定义主键")
    private Long itemId;

    /** 行数量，与 {@link #unitCode} 联合解释。 */
    @Schema(description = "数量")
    private BigDecimal itemQuantity;

    /** 计量单位持久化身份（字典或产品允许单位）。 */
    @Schema(description = "计量单位编码")
    private String unitCode;

    /** 批号；空时同意链可能按库存解析。 */
    @Schema(description = "批号")
    private String lotNumber;

    /**
     * 选品返回的库存行主键（{@code wkf_inventory_item.id}）；若传入，则草稿可用量门禁优先按该行精确比对，弱化批号字符串形态差异。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库存项主键（选品 inventoryItemId，可选）")
    private Long inventoryItemId;

    @Schema(description = "追溯码")
    private String traceNo;

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
    /** 业务发生时间。 */
    @Schema(description = "业务发生时间")
    private LocalDateTime occurrenceTime;
}
