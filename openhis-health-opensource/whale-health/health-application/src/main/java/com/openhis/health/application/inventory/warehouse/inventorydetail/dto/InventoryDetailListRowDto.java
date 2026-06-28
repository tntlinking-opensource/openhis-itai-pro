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

import java.math.BigDecimal;
import java.util.List;

/**
 * 现库库存明细列表行（IF-INV-DTL-02/03/06）。
 *
 * <p>对客字段：事实（ID/code/金额/倍数）+ 少量展示串（库存、名称）；字典/枚举文案由服务端按 code 拼 {@code *Label}。</p>
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
    @Schema(description = "收费项定义主键")
    private Long chargeItemDefinitionId;

    @Schema(description = "项目编码")
    private String productBusNo;

    @Schema(description = "项目名称")
    private String name;

    @Schema(description = "商品名称")
    private String merchandiseName;

    @Schema(description = "规格")
    private String totalVolume;

    @Schema(description = "厂家/产地")
    private String manufacturer;

    @Schema(description = "批准文号/国药准字")
    private String approvalNumber;

    @Schema(description = "医保码")
    private String ybNo;

    @Schema(description = "追溯码")
    private String traceNo;

    @Schema(description = "拼音码")
    private String pyStr;

    @Schema(description = "五笔码")
    private String wbStr;

    @Schema(description = "批号")
    private String lotNumber;

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

    @Schema(description = "效期展示")
    private String expirationDate;

    @Schema(description = "生产日期展示")
    private String productionDate;

    @Schema(description = "距效期剩余天数")
    private Integer remainingDays;

    @Schema(description = "供应状态码")
    private String inventoryStatusEnum;

    @Schema(description = "供应状态展示（服务端按枚举翻译）")
    private String inventoryStatusLabel;

    @Schema(description = "医保等级")
    private Integer chrgitmLv;

    @Schema(description = "医保等级展示")
    private String chrgitmLvLabel;

    @Schema(description = "药品分类编码")
    private String medicationCategoryCode;

    @Schema(description = "耗材分类编码")
    private String deviceCategoryCode;

    @Schema(description = "项目类别展示（药/耗分类之一）")
    private String categoryLabel;

    @Schema(description = "剂型编码")
    private String doseFormCode;

    @Schema(description = "剂型展示")
    private String doseFormLabel;

    @Schema(description = "包装单位编码（进价/售价/库存展示口径）")
    private String packageUnitCode;

    @Schema(description = "包装单位名")
    private String packageUnitName;

    @Schema(description = "拆零单位名")
    private String splitUnitName;

    @Schema(description = "拆零↔包装倍数（1 包装 = ratio 拆零）")
    private BigDecimal packageUnitRatio;

    @Schema(description = "库存（包装单位）展示，如 10盒")
    private String packageStockText;

    @Schema(description = "库存（拆零单位）展示，如 100片")
    private String splitStockText;

    @Schema(description = "批号进价（元/包装单位）")
    private BigDecimal lotCostPrice;

    @Schema(description = "批号售价（元/包装单位）")
    private BigDecimal lotSalePrice;

    @Schema(description = "进价行总额")
    private BigDecimal lineCostTotal;

    @Schema(description = "售价行总额")
    private BigDecimal linePriceTotal;

    @JsonIgnore
    @Schema(description = "拆零最小单位账面数量（内部换算）")
    private BigDecimal quantity;

    @JsonIgnore
    @Schema(description = "持久化单位编码（内部）")
    private String unitCode;

    @JsonIgnore
    @Schema(description = "销售包装→拆零倍数（内部）")
    private BigDecimal packageUnitRatioFallback;

    @JsonIgnore
    @Schema(description = "单位槽（内部换算）")
    private List<ProductDefinitionCatalogUnitBandDto> unitBands;
}
