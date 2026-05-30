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

package com.openhis.health.application.inventory.warehouse.inboundreceipt.request;

import com.openhis.health.application.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 *
 * <p>继承 {@link AbstractHealthPageRequest}（B.10.11）。{@code categoryCode} 等为分类/字典持久化码；
 * 勿与业务枚举的排序序号或整型展示值混用（B.10.8.1）。本类中 {@link #sortOrder} 仅表示列表排序方向，非枚举持久化字段。</p>
 *
 * <p>读模型查询由 Mapper
 * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper.InventoryWarehouseInboundReceiptMapper#toProductDefinitionPageQuery}
 * 转为 {@link com.openhis.health.application.querysupport.product.ProductDefinitionPageQuery}；返回行再经
 * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper.InventoryWarehouseInboundReceiptMapper#toPageProductsForInboundReceiptResponse}
 * 装配为 {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.response.PageProductsForInboundReceiptResponse}。
 * 应用层在分页返回前对 {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.ProductDefinitionItemDto} 二次写入<strong>实例维采购进价</strong>（
 * {@link com.openhis.health.domain.share.enums.PriceType#PURCHASE}，经 {@link com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService#resolveFirstPurchasePriceValue}）；<strong>不</strong>汇总目的库可用库存。</p>
 *
 * <p>应用入口：
 * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.service.InventoryWarehouseInboundReceiptAppService#pageProductsForInboundReceipt(PageProductsForInboundReceiptRequest)}。
 * 须携带 {@link #purposeLocationId}（目的药库，用于与进货头一致的药库门禁）。</p>
 */
@Getter
@Setter
public class PageProductsForInboundReceiptRequest extends AbstractHealthPageRequest {

    private Long purposeLocationId;

    /** 物品来源表名过滤（与产品定义读模型 {@code item_table} 语义一致）。 */
    private String productTableName;
    /** 物资分类编码（字典/分类体系持久化码，非枚举排序号）。 */
    private String categoryCode;
    /** 是否仅查启用态产品；可空由读模型默认。 */
    private Boolean activeFlag;
    /** 按供应商主键过滤；可空表示不按供应商收窄。 */
    private Long supplierId;
    /** 生产厂家关键字；下推读模型模糊匹配。 */
    private String manufacturer;
    /** 是否在结果中附带库存维度信息（读模型能力开关）。 */
    private Boolean includeInventory;
    /** 排序属性名（须与读模型白名单一致，否则被忽略）。 */
    private String sortField;
    /** 排序方向（如 {@code asc}/{@code desc}，大小写由读模型解释）。 */
    private String sortOrder;
}
