/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.purchaserequest.dto;

import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 科室采购申请详情明细行（IF-DEP-CPR-03）。
 *
 * <p>写模型字段来自 {@code wkf_supply_request} 行；品名/规格/厂家与 {@code unitBands} 由应用层批量 enrich。
 * 详情 enrich 锚持久化 {@code price}/{@code unitCode}（BR-PRICE-SRC-001）。不含批号/追溯码。</p>
 */
@Getter
@Setter
public class DeptPurchaseRequestDetailLineDto {

    /** 供应请求行技术主键。 */
    private Long id;

    /** 物品来源表名（如药品/耗材定义表）。 */
    private String itemTable;

    /** 物品定义主键。 */
    private Long itemId;

    /** 申请数量。 */
    private BigDecimal itemQuantity;

    /** 计量单位编码（字典持久化码，B.10.8.1）。 */
    private String unitCode;

    /** 计量单位展示名；读模型未命中时为 null。 */
    private String unitCodeDictText;

    /** 行单价（供应请求行持久化值，BR-PRICE-SRC-001）。 */
    private BigDecimal price;

    /** 行总价（供应请求行持久化值）。 */
    private BigDecimal totalPrice;

    /** 行备注（库列 {@code remake}）。 */
    private String remake;

    /** 物品名称回显；读模型未命中时为 null。 */
    private String productName;

    /** 规格展示（读模型 {@code totalVolume}）。 */
    private String specification;

    /** 厂家展示。 */
    private String manufacturer;

    /**
     * 产品目录编辑提示（单位槽与参考价）；AppService enrich 后供前端行内编辑与 slot 价展示。
     */
    private ProductDefinitionCatalogEditorHintsDto productCatalog;
}
