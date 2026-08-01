/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.purchaseorder.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 *
 * <p>继承 {@link AbstractHealthPageRequest}（B.10.11）。{@code categoryCode} 等为分类/字典持久化码；
 * 勿与业务枚举的排序序号或整型展示值混用（B.10.8.1）。本类中 {@link #sortOrder} 仅表示列表排序方向，非枚举持久化字段。</p>
 *
 * <p>读模型查询由 Mapper
 * {@link com.openhis.health.application.inventory.warehouse.purchaseorder.mapper.InventoryWarehousePurchaseOrderMapper#toProductDefinitionPageQuery}
 * 转为 {@link com.openhis.health.application.shared.product.ProductDefinitionPageQuery}；返回行再经
 * {@link com.openhis.health.application.inventory.warehouse.purchaseorder.mapper.InventoryWarehousePurchaseOrderMapper#toPageProductsForPurchaseResponse}
 * 装配为 {@link com.openhis.health.application.inventory.warehouse.purchaseorder.response.PageProductsForPurchaseResponse}。
 * 应用层在分页返回前对 {@link com.openhis.health.application.inventory.warehouse.purchaseorder.dto.ProductDefinitionItemDto} 二次写入<strong>实例维采购进价</strong>（
 * {@link com.openhis.health.domain.share.enums.PriceType#PURCHASE}，经 {@link com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService#resolveFirstPurchasePriceValue}）；
 * <strong>不</strong>汇总目的库可用库存（{@code availableQuantity} 恒为 {@code null}）。</p>
 *
 * <p>应用入口：
 * {@link com.openhis.health.application.inventory.warehouse.purchaseorder.service.InventoryWarehousePurchaseOrderAppService#pageProductsForPurchase(PageProductsForPurchaseRequest)}。
 * 须携带 {@link #purposeLocationId}（目的药库，用于与订货头一致的药库门禁）。
 * 可选 {@link #productDefinitionIds}：预警快捷勾选后按 id 集合收窄（IF-WHS-PO-14→11）。</p>
 */
@Getter
@Setter
public class PageProductsForPurchaseRequest extends AbstractHealthPageRequest {

    /**
     * 目的药库位置主键：与订货单头 {@code purpose_location_id} 及列表/详情门禁锚点一致；必填。
     */
    private Long purposeLocationId;

    /**
     * 物品来源表名过滤（与产品定义读模型 {@code item_table} 语义一致；须在 AppService 白名单内）。
     */
    private String productTableName;

    /**
     * 预警勾选带回的产品定义主键集合；非空时按 id 收窄选品结果（仍须 {@link #productTableName}/启用门禁）。
     * 用于 IF-WHS-PO-14→11 快捷路径；空或 null 表示普通关键字分页选品。
     */
    private List<Long> productDefinitionIds;

    /**
     * 物资分类编码（字典/分类体系持久化码，非枚举排序号）。
     */
    private String categoryCode;

    /**
     * 是否仅查启用态产品；可空由读模型默认。
     */
    private Boolean activeFlag;

    /**
     * 按供应商主键过滤；可空表示不按供应商收窄。
     */
    private Long supplierId;

    /**
     * 生产厂家关键字；下推读模型模糊匹配。
     */
    private String manufacturer;

    /**
     * 是否在结果中附带库存维度信息（读模型能力开关；订货选品路径仍不汇总 {@code availableQuantity}）。
     */
    private Boolean includeInventory;

    /**
     * 排序属性名（须与读模型白名单一致，否则被忽略）。
     */
    private String sortField;

    /**
     * 排序方向（如 {@code asc}/{@code desc}，大小写由读模型解释）。
     */
    private String sortOrder;
}
