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

package com.openhis.health.application.inpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * IF-IP-DOR-11：医嘱开立目录下的库存可用行（同就诊发放预减后净量）。
 *
 * <p>嵌于 {@link InpatientDoctorStationAdviceCatalogRowDto#getInventoryList()}；药品/耗材按批号展示
 * {@link #price}（LOT_NUMBER_PRICE 与 {@link #lotNumber} 对齐）。住院目录以药为主，耗材行视产品表而定。</p>
 */
@Data
@Accessors(chain = true)
/** 医生站医嘱目录库存行。 */
@Schema(description = "医生站医嘱目录库存行")
public class InpatientDoctorStationAdviceInventoryRowDto {

    @Schema(description = "物品主表名")
    private String itemTable;

    /** 物品定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 物品定义主键。 */
    @Schema(description = "物品定义主键")
    private Long itemId;

    /** 目录类别。 */
    @Schema(description = "目录类别")
    private String categoryCode;

    /** 库存行主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 库存行主键。 */
    @Schema(description = "库存行主键")
    private Long inventoryId;

    /** 预减后剩余量。 */
    @Schema(description = "预减后剩余量（小单位）")
    private BigDecimal quantity;

    /** 小单位编码。 */
    @Schema(description = "小单位编码")
    private String unitCode;

    /** 与 {@link #unitCode} 对应的单位展示名。 */
    @Schema(description = "小单位展示名")
    private String unitName;

    /** 批号（与定价表 {@code adm_charge_item_definition.lot_no} 对齐，用于解析 {@code LOT_NUMBER_PRICE}）。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 库位 id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 库位主键。 */
    @Schema(description = "库位主键")
    private Long locationStoreId;

    /** 库房 id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 库房主键。 */
    @Schema(description = "库房主键")
    private Long locationId;

    /** 库房名称。 */
    @Schema(description = "库房名称")
    private String locationName;

    /**
     * 该库存行批号对应的批号售价（元），来源 {@code adm_charge_item_definition.price_value}，
     * 价型 {@code LOT_NUMBER_PRICE} 且 {@code lot_no} 与本行 {@link #lotNumber} 匹配。
     */
    @Schema(description = "批号售价（LOT_NUMBER_PRICE，随批号变化）")
    private BigDecimal price;

    /** 与 {@link #price} 对应的收费项定义主键，开立锚定价明细时用。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "批号售价定价行主键")
    private Long chargeItemDefinitionId;

    @Schema(description = "展示顺序")
    private Integer displayOrder;
}
