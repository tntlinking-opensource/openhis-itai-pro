/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.purchaserequest.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 科室采购申请选品分页请求（IF-DEP-CPR-11；与药库订货 IF-WHS-PO-11 同构，无供应商筛选）。
 *
 * <p>应用入口：
 * {@link com.openhis.health.application.inventory.department.purchaserequest.service.InventoryDepartmentPurchaseRequestAppService#pageProductsForDeptPurchaseRequest(PageProductsForDeptPurchaseRequestRequest)}。</p>
 */
@Getter
@Setter
public class PageProductsForDeptPurchaseRequestRequest extends AbstractHealthPageRequest {

    /** 目的科室药房位置主键：用于科室药房门禁；必填。 */
    private Long purposeLocationId;

    /** 物品来源表名过滤（须在 AppService 白名单内）。 */
    private String productTableName;

    /** 物资分类编码（字典持久化码）。 */
    private String categoryCode;

    /** 是否仅查启用态产品。 */
    private Boolean activeFlag;

    /** 生产厂家关键字。 */
    private String manufacturer;

    /** 是否在结果中附带库存维度信息（选品路径仍不汇总 availableQuantity）。 */
    private Boolean includeInventory;

    /** 排序属性名。 */
    private String sortField;

    /** 排序方向（如 asc/desc）。 */
    private String sortOrder;
}
