/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.inboundreceipt.dto;

import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 药库入库选品分页行（IF-WHS-IN-11）。
 *
 * <p>与前端选品表、明细行 {@code itemTable}/{@code itemId} 对齐；编码类字段均为字典或计量体系下的
 * <strong>持久化字符串</strong>（B.10.8.1），与枚举/字典 {@code getCode()} 同义。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "药库入库选品分页行")
public class ProductDefinitionItemDto {

    /** 产品定义主键。 */
    @Schema(description = "产品定义主键")
    private Long productDefinitionId;

    /** 物品来源物理表名。 */
    @Schema(description = "物品来源物理表名")
    private String productTableName;

    /** 目录业务编号 productBusNo。 */
    @Schema(description = "目录业务编号 productBusNo")
    private String productBusNo;

    /** 物品名称。 */
    @Schema(description = "物品名称")
    private String productName;

    /** 商品名 merchandiseName。 */
    @Schema(description = "商品名 merchandiseName")
    private String merchandiseName;

    /** 计量单位编码。 */
    @Schema(description = "计量单位编码")
    private String unitCode;

    /** 拆零单位 minUnitCode。 */
    @Schema(description = "拆零单位 minUnitCode")
    private String minUnitCode;

    /** 物资分类编码。 */
    @Schema(description = "物资分类编码")
    private String categoryCode;

    /** 物资分类展示名。 */
    @Schema(description = "物资分类展示名")
    private String categoryCodeDictText;

    /** 基础标价。 */
    @Schema(description = "基础标价")
    private BigDecimal price;

    /** 采购进价。 */
    @Schema(description = "采购进价")
    private BigDecimal purchasePrice;

    /** 可用库存量。 */
    @Schema(description = "可用库存量")
    private BigDecimal availableQuantity;

    /** 单位四槽。 */
    @Schema(description = "单位四槽")
    private List<ProductDefinitionCatalogUnitBandDto> unitBands;

    /** 厂家合一。 */
    @Schema(description = "厂家合一")
    private String manufacturer;

    /** 规格 totalVolume。 */
    @Schema(description = "规格 totalVolume")
    private String totalVolume;

    /** 拼音 pyStr。 */
    @Schema(description = "拼音 pyStr")
    private String pyStr;

    /** 五笔 wbStr。 */
    @Schema(description = "五笔 wbStr")
    private String wbStr;

    /**
     * 追溯码贴码单位（药典；可游离于五类业务单位）。
     */
    @Schema(description = "追溯码贴码单位")
    private String traceUnitCode;

    /**
     * 1 个追溯码单位对应的拆零数量。
     */
    @Schema(description = "追溯码单位→拆零倍数")
    private BigDecimal traceUnitRatio;

    /**
     * 门诊拆分属性（OrderCalcRule：1/3=拆零单位可拆，2/4=包装单位）。
     * <p>码上放心确认生成时：拆零属性须小码入库；包装属性允许中大码按包装单位入库。</p>
     */
    @Schema(description = "门诊拆分属性编码")
    private String partAttributeEnum;

    /** 门诊拆分属性展示名。 */
    @Schema(description = "门诊拆分属性展示名")
    private String partAttributeEnumDictText;
}
