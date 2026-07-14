/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.purchaseorder.dto;

import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 *
 * <p>写模型字段来自 {@code wkf_supply_request} 行；{@code productName}/{@code specification}/{@code manufacturer}、
 * {@code unitCodeDictText} 等读模型列仅在应用层批量命中产品定义读模型时填充，未命中时为 null。
 * {@link #productCatalog} 与选品列表在单位槽与参考价维度对齐；详情 enrich 锚持久化 {@code price}/{@code unitCode}
 *（BR-PRICE-SRC-001），不以当前价覆盖写库值。不含批号/追溯码（订货场景）。</p>
 */
@Getter
@Setter
public class PurchaseOrderDetailLineDto {

    /** 供应请求行技术主键。 */
    private Long id;

    /** 物品来源表名（如药品/耗材定义表）。 */
    private String itemTable;

    /** 物品定义主键。 */
    private Long itemId;

    /** 订货数量。 */
    private BigDecimal itemQuantity;

    /** 计量单位编码（字典持久化码，B.10.8.1）。 */
    private String unitCode;

    /**
     * 计量单位展示名：与 {@link #unitCode} 命中产品读模型采购/拆零等单位槽时取自读模型；
     * 未命中或无法对齐时为 {@code null}。
     */
    private String unitCodeDictText;

    /** 行单价（供应请求行持久化值，BR-PRICE-SRC-001）。 */
    private BigDecimal price;

    /** 行总价（供应请求行持久化值）。 */
    private BigDecimal totalPrice;

    /** 期望或业务发生时间（{@code occurrence_time}，制单时由前端传入）。 */
    private LocalDateTime occurrenceTime;

    /** 行备注（库列 {@code remake}）。 */
    private String remake;

    /** 物品名称回显；读模型未命中时为 {@code null}。 */
    private String productName;

    /** 规格展示（读模型 {@code totalVolume}）；未命中时为 {@code null}。 */
    private String specification;

    /** 厂家展示；读模型未命中时为 {@code null}。 */
    private String manufacturer;

    /**
     * 产品目录编辑提示（单位槽与参考价）；AppService enrich 后供前端行内编辑与 slot 价展示。
     */
    private ProductDefinitionCatalogEditorHintsDto productCatalog;
}
