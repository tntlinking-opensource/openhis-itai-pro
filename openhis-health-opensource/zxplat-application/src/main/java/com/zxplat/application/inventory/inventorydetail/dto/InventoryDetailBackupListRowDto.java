/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.inventorydetail.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 备份库存明细列表行（IF-INV-DTL-04/05/07）。
 *
 * <p>对客字段与 {@link InventoryDetailListRowDto} 同构；{@code id} 语义为源库存项主键；四价直读静表冻结列。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "备份库存明细行")
public class InventoryDetailBackupListRowDto {

    @JsonSerialize(using = ToStringSerializer.class)
/** 源库存项主键（与现库列表 id 语义一致）。 */
@Schema(description = "源库存项主键（与现库列表 id 语义一致）")
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
    @Schema(description = "收费项定义主键")
    private Long chargeItemDefinitionId;

    /** 项目编码。 */

    @Schema(description = "项目编码")
    private String productBusNo;

    /** 项目名称。 */

    @Schema(description = "项目名称")
    private String name;

    /** 商品名称。 */

    @Schema(description = "商品名称")
    private String merchandiseName;

    /** 规格。 */

    @Schema(description = "规格")
    private String totalVolume;

    /** 厂家/产地。 */

    @Schema(description = "厂家/产地")
    private String manufacturer;

    /** 批准文号/国药准字。 */

    @Schema(description = "批准文号/国药准字")
    private String approvalNumber;

    /** 医保码。 */

    @Schema(description = "医保码")
    private String ybNo;

    /** 追溯码。 */

    @Schema(description = "追溯码")
    private String traceNo;

    /** 拼音码。 */

    @Schema(description = "拼音码")
    private String pyStr;

    /** 五笔码。 */

    @Schema(description = "五笔码")
    private String wbStr;

    /** 批号。 */

    @Schema(description = "批号")
    private String lotNumber;

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

    /** 效期展示。 */

    @Schema(description = "效期展示")
    private String expirationDate;

    /** 生产日期展示。 */

    @Schema(description = "生产日期展示")
    private String productionDate;

    /** 距效期剩余天数。 */

    @Schema(description = "距效期剩余天数")
    private Integer remainingDays;

    /** 供应状态码。 */

    @Schema(description = "供应状态码")
    private String inventoryStatusEnum;

    /** 供应状态展示。 */

    @Schema(description = "供应状态展示")
    private String inventoryStatusLabel;

    /** 医保等级。 */

    @Schema(description = "医保等级")
    private Integer chrgitmLv;

    /** 医保等级展示。 */

    @Schema(description = "医保等级展示")
    private String chrgitmLvLabel;

    /** 药品分类编码。 */

    @Schema(description = "药品分类编码")
    private String medicationCategoryCode;

    /** 耗材分类编码。 */

    @Schema(description = "耗材分类编码")
    private String deviceCategoryCode;

    /** 项目类别展示。 */

    @Schema(description = "项目类别展示")
    private String categoryLabel;

    /** 剂型编码。 */

    @Schema(description = "剂型编码")
    private String doseFormCode;

    /** 剂型展示。 */

    @Schema(description = "剂型展示")
    private String doseFormLabel;

    /** 包装单位编码。 */

    @Schema(description = "包装单位编码")
    private String packageUnitCode;

    /** 包装单位名。 */

    @Schema(description = "包装单位名")
    private String packageUnitName;

    /** 拆零单位名。 */

    @Schema(description = "拆零单位名")
    private String splitUnitName;

    /** 拆零↔包装倍数。 */

    @Schema(description = "拆零↔包装倍数")
    private BigDecimal packageUnitRatio;

    /**
     * 包装拆零比例展示（服务端拼装），形如 {@code 1盒/36片}。
     *
     * <p>与临床医嘱装量后缀 {@code 1片x100} 口径不同；由静表倍数 + 包装/拆零单位名拼装。</p>
     */
    @Schema(description = "包装拆零比例展示，如 1盒/36片")
    private String packageSplitRatioText;

    /** 库存（包装单位）展示。 */

    @Schema(description = "库存（包装单位）展示")
    private String packageStockText;

    /** 库存（拆零单位）展示。 */

    @Schema(description = "库存（拆零单位）展示")
    private String splitStockText;

    /** 批号进价（元/包装单位，静表冻结）。 */

    @Schema(description = "批号进价（元/包装单位，静表冻结）")
    private BigDecimal lotCostPrice;

    /** 批号售价（元/包装单位，静表冻结）。 */

    @Schema(description = "批号售价（元/包装单位，静表冻结）")
    private BigDecimal lotSalePrice;

    /** 进价行总额（静表冻结）。 */

    @Schema(description = "进价行总额（静表冻结）")
    private BigDecimal lineCostTotal;

    /** 售价行总额（静表冻结）。 */

    @Schema(description = "售价行总额（静表冻结）")
    private BigDecimal linePriceTotal;

    @JsonIgnore
    /** 拆零最小单位账面数量（内部换算）。 */
    @Schema(description = "拆零最小单位账面数量（内部换算）")
    private BigDecimal quantity;

    @JsonIgnore
    /** 单位槽（内部展示 enrich）。 */
    @Schema(description = "单位槽（内部展示 enrich）")
    private List<ProductDefinitionCatalogUnitBandDto> unitBands;
}
