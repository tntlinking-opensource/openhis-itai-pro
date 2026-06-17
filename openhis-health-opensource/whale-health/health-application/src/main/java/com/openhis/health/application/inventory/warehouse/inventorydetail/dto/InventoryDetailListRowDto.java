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

package com.openhis.health.application.inventory.warehouse.inventorydetail.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogUnitBandDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 现库库存明细列表行（IF-INV-DTL-02/03/06）。
 */
@Getter
@Setter
@Accessors(chain = true)
/** 现库库存明细行。 */
@Schema(description = "现库库存明细行")
public class InventoryDetailListRowDto {

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库存项主键")
    private Long id;

    /** 物品表名。 */
    @Schema(description = "物品表名")
    private String itemTable;

    @JsonSerialize(using = ToStringSerializer.class)
    /** 物品定义主键。 */
    @Schema(description = "物品定义主键")
    private Long itemId;

    @JsonSerialize(using = ToStringSerializer.class)
    /** 收费项定义主键。 */
    @Schema(description = "收费项定义主键（来自产品定义读模型）")
    private Long chargeItemDefinitionId;

    /** 名称。 */
    @Schema(description = "名称")
    private String name;

    /** 拼音码。 */
    @Schema(description = "拼音码")
    private String pyStr;

    /** 五笔码。 */
    @Schema(description = "五笔码")
    private String wbStr;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    @JsonIgnore
    /** 拆零最小单位数量。 */
    @Schema(description = "拆零最小单位数量（内部金额换算用，不对客展示）")
    private BigDecimal quantity;

    @JsonIgnore
    /** 持久化单位编码。 */
    @Schema(description = "持久化单位编码（不对客展示）")
    private String unitCode;

    /** 主计价单位展示名。 */
    @Schema(description = "主计价单位展示名（兼容字段）")
    private String unitName;

    /** 库存数量展示串。 */
    @Schema(description = "库存数量展示串（如 2盒3片；服务端拼接，禁止小数盒）")
    private String displayQuantityText;

    /** 进价单价。 */
    @Schema(description = "进价单价（元/采购包装单位）")
    private BigDecimal displayCostUnitPrice;

    /** 进价单价对应单位名。 */
    @Schema(description = "进价单价对应单位名")
    private String displayCostUnitName;

    /** 售价单价。 */
    @Schema(description = "售价单价（元/销售包装单位）")
    private BigDecimal displayPriceUnitPrice;

    /** 售价单价对应单位名。 */
    @Schema(description = "售价单价对应单位名")
    private String displayPriceUnitName;

    @JsonSerialize(using = ToStringSerializer.class)
    /** 库房主键。 */
    @Schema(description = "库房主键")
    private Long warehouseId;

    /** 库房名称。 */
    @Schema(description = "库房名称")
    private String warehouseName;

    @JsonSerialize(using = ToStringSerializer.class)
    /** 供应商主键。 */
    @Schema(description = "供应商主键")
    private Long supplierId;

    /** 供应商名称。 */
    @Schema(description = "供应商名称")
    private String supplierName;

    /** 效期展示串。 */
    @Schema(description = "效期展示串")
    private String expirationDate;

    /** 生产日期展示串。 */
    @Schema(description = "生产日期展示串")
    private String productionDate;

    /** 供应状态持久化码。 */
    @Schema(description = "供应状态持久化码")
    private String inventoryStatusEnum;

    /** 供应状态展示。 */
    @Schema(description = "供应状态展示")
    private String inventoryStatusText;

    /** 医保等级。 */
    @Schema(description = "医保等级")
    private Integer chrgitmLv;

    /** 医保等级展示。 */
    @Schema(description = "医保等级展示")
    private String chrgitmLvText;

    /** 药品分类编码。 */
    @Schema(description = "药品分类编码")
    private String medicationCategoryCode;

    /** 药品分类展示。 */
    @Schema(description = "药品分类展示")
    private String medicationCategoryText;

    /** 耗材分类编码。 */
    @Schema(description = "耗材分类编码")
    private String deviceCategoryCode;

    /** 耗材分类展示。 */
    @Schema(description = "耗材分类展示")
    private String deviceCategoryText;

    @JsonIgnore
    /** 采购包装→拆零倍数。 */
    @Schema(description = "采购包装→拆零倍数（内部金额换算，不对客下发）")
    private BigDecimal purchaseUnitRatio;

    @JsonIgnore
    /** 销售包装→拆零倍数。 */
    @Schema(description = "销售包装→拆零倍数（内部金额换算，不对客下发）")
    private BigDecimal packageUnitRatio;

    @JsonIgnore
    /** 批号进价整包金额。 */
    @Schema(description = "批号进价整包金额（内部；展示见 displayCostUnitPrice）")
    private BigDecimal lotNumberCostAmount;

    @JsonIgnore
    /** 批号售价整包金额。 */
    @Schema(description = "批号售价整包金额（内部；展示见 displayPriceUnitPrice）")
    private BigDecimal lotNumberPriceAmount;

    /** 进价链行总额。 */
    @Schema(description = "进价链行总额")
    private BigDecimal lineCostTotal;

    /** 售价链行总额。 */
    @Schema(description = "售价链行总额")
    private BigDecimal linePriceTotal;

    @JsonIgnore
    /** 历史字段。 */
    @Schema(description = "历史字段，已由 displayCostUnitPrice 替代")
    private BigDecimal displayCostPerSplit;

    @JsonIgnore
    /** 历史字段。 */
    @Schema(description = "历史字段，已由 displayPriceUnitPrice 替代")
    private BigDecimal displayPricePerSplit;

    /**
     * 单位槽（采购/包装/拆零/库存）及相对拆零倍数；仅服务端换算用，不对客下发。
     */
    @JsonIgnore
    @Schema(description = "单位槽（内部换算，不对客下发）")
    private List<ProductDefinitionCatalogUnitBandDto> unitBands;
}
