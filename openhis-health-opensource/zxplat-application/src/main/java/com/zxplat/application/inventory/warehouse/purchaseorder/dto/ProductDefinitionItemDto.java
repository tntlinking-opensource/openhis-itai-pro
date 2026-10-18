/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.purchaseorder.dto;

import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 *
 * <p>与前端选品表、明细行 {@code itemTable}/{@code itemId} 对齐：{@code productTableName} 与 {@code productDefinitionId}
 * 组成物品复合键；{@code unitCode} 等编码类字段均为字典或计量体系下的<strong>持久化字符串</strong>（B.10.8.1）。
 * 与进货选品 {@link com.zxplat.application.inventory.warehouse.inboundreceipt.dto.ProductDefinitionItemDto} 字段同构；
 * 订货路径 {@link #availableQuantity} 由 AppService 固定写 {@code null}。</p>
 */
@Getter
@Setter
public class ProductDefinitionItemDto {

    /**
     * 产品定义主键：与读模型 {@code product_definition_id} 一致，写入订货明细时映射为 {@code itemId}。
     */
    private Long productDefinitionId;

    /**
     * 物品来源物理表名：与供应/库存域 {@code item_table} 一致（白名单通常为药品定义表或耗材定义表）。
     */
    private String productTableName;

    /**
     * 产品业务流水号：读模型 {@code product_bus_no}，用于列表展示与对账检索，非技术主键。
     */
    private String productBusNo;

    /** 通用名/注册名等产品主展示名称。 */
    private String productName;

    /** 商品名或商用名；无单独商用名时可能与 {@link #productName} 同源或为空。 */
    private String merchandiseName;

    /**
     * 采购/业务默认计量单位编码：读模型采购单位列映射，与订货明细 {@code unitCode} 候选一致（字典持久化码）。
     */
    private String unitCode;

    /**
     * 最小包装（拆零）单位编码：与 {@link #unitBands} 中拆零槽 {@code bandKind=SPLIT} 一致（字典持久化码）。
     */
    private String minUnitCode;

    /** 物资分类编码：字典持久化码，用于选品侧分类筛选与展示。 */
    private String categoryCode;

    /**
     * 物资分类展示名（读模型 {@code category_code_dict_text}）：选品下拉「项目种类」等与编码并列的展示字段。
     */
    private String categoryCodeDictText;

    /**
     * 基础标价：读模型直接给出的参考售价口径（与选品列表 {@link #purchasePrice} 采购进价列来源不同）。
     */
    private BigDecimal price;

    /**
     * 采购进价：定价表 {@link com.zxplat.domain.share.enums.PriceType#PURCHASE} 实例维；
     * 由 AppService enrich 写入，Mapper 初装亦尝试解析。
     */
    private BigDecimal purchasePrice;

    /**
     * 可用库存量：订货选品路径恒为 {@code null}（与进货选品字段同构但语义不同，不汇总目的库库存）。
     */
    private BigDecimal availableQuantity;

    /**
     * 订货选品单位槽：采购、包装、拆零、库存；
     * 定价表 {@code PURCHASE} 每包装进价写各槽 {@code unitPrice}（§2.2.3 读价 enrich，不写库）。
     */
    private List<ProductDefinitionCatalogUnitBandDto> unitBands;

    /**
     * 生产厂家及产地合一展示：与产品定义读模型 {@code manufacturer} 列对齐。
     */
    private String manufacturer;

    /**
     * 包装规格或总量描述：读模型 {@code total_volume}，常用于列表「规格」列展示。
     */
    private String totalVolume;

    /** 拼音检索码：与读模型检索列一致，供前端本地或服务端关键字辅助。 */
    private String pyStr;

    /** 五笔检索码：与读模型检索列一致。 */
    private String wbStr;
}
