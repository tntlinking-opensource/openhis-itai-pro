/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.product.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单据明细编辑态用的「产品目录提示」：与进货选品 {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.ProductDefinitionItemDto}、
 * 药库出库详情 {@link com.openhis.health.application.inventory.warehouse.stockout.dto.StockOutDetailLineDto} 在<strong>单位槽</strong>与<strong>参考价</strong>维度对齐，
 *
 * <p>{@link #purchasePrice} 须由应用层经定价表价类 {@link com.openhis.health.domain.share.enums.PriceType#PURCHASE} 解析后写入；
 * 读模型装配阶段保持 {@code null}。{@link #purchaseUnitRatioToSplit} 由读模型 {@code purchase_unit_ratio} 装配，供将「按采购包装标价」的金额换算为各槽 {@code unitPrice}（元，六位小数四舍五入）。</p>
 */
@Getter
@Setter
public class ProductDefinitionCatalogEditorHintsDto {

    /**
     * 与进货选品 {@code unitBands} 同构：采购 / 包装（PACKAGE）/ 拆零 / 库存及 {@code ratioToSplit}；
     * 详情接口中可由应用层按行单价或采购参考价写入各槽 {@code unitPrice}（换算后单价，元）。
     */
    private List<ProductDefinitionCatalogUnitBandDto> unitBands;

    /**
     * 展示用采购单价：定价表 {@code PURCHASE}；无命中时为 {@code null}。
     */
    private BigDecimal purchasePrice;

    /**
     * 读模型基础标价（与选品行 {@code price} 同源列），与行内已录入 {@code price} 区分。
     */
    private BigDecimal productBasePrice;

    /** 拆零单位编码（读模型）。 */
    private String minUnitCode;

    /** 读模型默认采购单位编码（与选品行 {@code unitCode} 同源）。 */
    private String defaultPurchaseUnitCode;

    /**
     * 读模型 {@code purchase_unit_ratio}：采购包装单位相对拆零单位的换算倍数；用于将定价表 {@code PURCHASE} 等「按采购包装标价」的金额换算为各槽 {@code unitPrice}。
     */
    private BigDecimal purchaseUnitRatioToSplit;

    /** 读模型通称 / 商品名（与品名展示列同源，可空）。 */
    private String merchandiseName;

    /** 产品分类持久化身份（字典 {@code code}）。 */
    private String categoryCode;

    /** 产品分类人读文案（字典 display，可空）。 */
    private String categoryCodeDictText;

    /** 读模型业务流水号。 */
    private String productBusNo;

    /** 读模型 {@code total_volume}（规格/装量描述）。 */
    private String totalVolume;
}
