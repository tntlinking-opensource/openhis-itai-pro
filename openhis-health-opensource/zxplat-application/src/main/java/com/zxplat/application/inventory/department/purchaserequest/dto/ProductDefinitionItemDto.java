/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.purchaserequest.dto;

import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 科室采购申请选品行（IF-DEP-CPR-11）。
 *
 * <p>与药库订货选品同构：{@code productTableName} 与 {@code productDefinitionId} 组成物品复合键；
 * {@code availableQuantity} 由 AppService 固定写 {@code null}（申请单不校验/不汇总库存，BR-DEP-CPR-010）。</p>
 */
@Getter
@Setter
public class ProductDefinitionItemDto {

    /** 产品定义主键：写入申请明细时映射为 {@code itemId}。 */
    private Long productDefinitionId;

    /** 物品来源物理表名（药品/耗材定义表白名单；与 {@code item_table} 一致）。 */
    private String productTableName;

    /** 产品业务流水号（读模型 {@code product_bus_no}，展示与检索用）。 */
    private String productBusNo;

    /** 通用名/注册名等产品主展示名称。 */
    private String productName;

    /** 商品名或商用名；无单独商用名时可能与 {@link #productName} 同源。 */
    private String merchandiseName;

    /** 采购/业务默认计量单位编码（字典持久化码，B.10.8.1）。 */
    private String unitCode;

    /** 最小包装（拆零）单位编码；与 {@link #unitBands} 拆零槽一致。 */
    private String minUnitCode;

    /** 物资分类编码（字典持久化码）。 */
    private String categoryCode;

    /** 物资分类展示名（读模型 category_code_dict_text）。 */
    private String categoryCodeDictText;

    /** 基础标价：读模型参考售价口径（与 {@link #purchasePrice} 采购进价不同源）。 */
    private BigDecimal price;

    /**
     * 采购进价：定价表 {@link com.zxplat.domain.share.enums.PriceType#PURCHASE} 实例维；
     * 由 AppService 经 {@code ChargeItemDefinitionQueryService#resolveFirstPurchasePriceValue} enrich。
     */
    private BigDecimal purchasePrice;

    /**
     * 可用库存量：科室采购选品路径恒为 {@code null}（与订货同构字段位，但不汇总库存）。
     */
    private BigDecimal availableQuantity;

    /**
     * 选品单位槽（采购/包装/拆零等）；各槽 {@code unitPrice} 由 PURCHASE 进价换算写入（只读 enrich，不写库）。
     */
    private List<ProductDefinitionCatalogUnitBandDto> unitBands;

    /** 生产厂家及产地合一展示。 */
    private String manufacturer;

    /** 包装规格或总量描述（读模型 {@code total_volume}）。 */
    private String totalVolume;

    /** 拼音检索码。 */
    private String pyStr;

    /** 五笔检索码。 */
    private String wbStr;
}
