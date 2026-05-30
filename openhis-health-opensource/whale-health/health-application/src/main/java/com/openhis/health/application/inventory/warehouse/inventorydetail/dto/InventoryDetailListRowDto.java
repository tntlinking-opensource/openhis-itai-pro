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
import com.openhis.health.application.common.dto.product.ProductDefinitionCatalogUnitBandDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 现库库存明细列表行（IF-INV-DTL-02/03/06）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "现库库存明细行")
public class InventoryDetailListRowDto {

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库存项主键")
    private Long id;

    @Schema(description = "物品表名")
    private String itemTable;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "物品定义主键")
    private Long itemId;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "收费项定义主键（来自产品定义读模型）")
    private Long chargeItemDefinitionId;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "拼音码")
    private String pyStr;

    @Schema(description = "五笔码")
    private String wbStr;

    @Schema(description = "批号")
    private String lotNumber;

    @Schema(description = "拆零最小单位数量（内部换算用，界面请用 displayQuantity）")
    private BigDecimal quantity;

    @JsonIgnore
    @Schema(description = "持久化单位编码（不对客展示）")
    private String unitCode;

    @Schema(description = "主计价单位展示名（兼容字段；列表数量单位见 displayUnitName）")
    private String unitName;

    @Schema(description = "界面展示数量（库存单位，已由拆零换算）")
    private BigDecimal displayQuantity;

    @Schema(description = "界面展示数量单位名（库存单位）")
    private String displayUnitName;

    @Schema(description = "进价单价（元/采购包装单位）")
    private BigDecimal displayCostUnitPrice;

    @Schema(description = "进价单价对应单位名")
    private String displayCostUnitName;

    @Schema(description = "售价单价（元/销售包装单位）")
    private BigDecimal displayPriceUnitPrice;

    @Schema(description = "售价单价对应单位名")
    private String displayPriceUnitName;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库房主键")
    private Long warehouseId;

    @Schema(description = "库房名称")
    private String warehouseName;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "供应商主键")
    private Long supplierId;

    @Schema(description = "供应商名称")
    private String supplierName;

    @Schema(description = "效期展示串")
    private String expirationDate;

    @Schema(description = "生产日期展示串")
    private String productionDate;

    @Schema(description = "供应状态持久化码")
    private String inventoryStatusEnum;

    @Schema(description = "供应状态展示")
    private String inventoryStatusText;

    @Schema(description = "医保等级")
    private Integer chrgitmLv;

    @Schema(description = "医保等级展示")
    private String chrgitmLvText;

    @Schema(description = "药品分类编码")
    private String medicationCategoryCode;

    @Schema(description = "药品分类展示")
    private String medicationCategoryText;

    @Schema(description = "耗材分类编码")
    private String deviceCategoryCode;

    @Schema(description = "耗材分类展示")
    private String deviceCategoryText;

    @JsonIgnore
    @Schema(description = "采购包装→拆零倍数（内部金额换算，不对客下发）")
    private BigDecimal purchaseUnitRatio;

    @JsonIgnore
    @Schema(description = "销售包装→拆零倍数（内部金额换算，不对客下发）")
    private BigDecimal packageUnitRatio;

    @JsonIgnore
    @Schema(description = "批号进价整包金额（内部；展示见 displayCostUnitPrice）")
    private BigDecimal lotNumberCostAmount;

    @JsonIgnore
    @Schema(description = "批号售价整包金额（内部；展示见 displayPriceUnitPrice）")
    private BigDecimal lotNumberPriceAmount;

    @Schema(description = "进价链行总额")
    private BigDecimal lineCostTotal;

    @Schema(description = "售价链行总额")
    private BigDecimal linePriceTotal;

    @JsonIgnore
    @Schema(description = "历史字段，已由 displayCostUnitPrice 替代")
    private BigDecimal displayCostPerSplit;

    @JsonIgnore
    @Schema(description = "历史字段，已由 displayPriceUnitPrice 替代")
    private BigDecimal displayPricePerSplit;

    /**
     * 单位槽（采购/包装/拆零/库存）及相对拆零倍数；仅服务端换算用，不对客下发。
     */
    @JsonIgnore
    @Schema(description = "单位槽（内部换算，不对客下发）")
    private List<ProductDefinitionCatalogUnitBandDto> unitBands;
}
