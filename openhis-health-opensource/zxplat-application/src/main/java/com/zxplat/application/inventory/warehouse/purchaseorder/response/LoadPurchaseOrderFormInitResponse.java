/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.purchaseorder.response;

import com.zxplat.framework.core.application.dto.OptionDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 *
 * <p>由 {@link com.zxplat.application.inventory.warehouse.purchaseorder.mapper.InventoryWarehousePurchaseOrderMapper#toLoadPurchaseOrderFormInitResponse}
 * 及若干 {@code toOptionDtoList*} 装配；与 {@link com.zxplat.application.inventory.warehouse.purchaseorder.request.LoadPurchaseOrderFormInitRequest} 成对使用（IF-WHS-PO-01）。</p>
 *
 * <p>下拉项统一为 {@link OptionDto}（B.10.5）：{@code sort} 为排序权重，{@code value} 为前端绑定值（字符串），{@code label} 为展示文案。</p>
 *
 * <p>{@code warehouseLocationOptions} 首项约定 {@code value="0"}、{@code label="全部"}，
 * 供列表 {@link com.zxplat.application.inventory.warehouse.purchaseorder.request.PagePurchaseOrdersRequest#getPurposeLocationId()} 筛选「全部药库」。</p>
 */
@Getter
@Setter
public class LoadPurchaseOrderFormInitResponse {

    /**
     * 药库库位下拉：首项为「全部」筛选项（{@code value="0"}），其余为当前用户管辖内启用药库。
     */
    private List<OptionDto> warehouseLocationOptions;

    /**
     * 科室药房下拉（关联科室采购申请弹窗）：首项「全部」{@code value="0"}，其余为院内启用科室药房。
     */
    private List<OptionDto> departmentPharmacyLocationOptions;

    /**
     * 启用供应商下拉：{@code value} 为供应商主键字符串，{@code label} 含类型展示后缀。
     */
    private List<OptionDto> supplierOptions;

    /**
     * 库管员下拉：本机构 {@code ROLE_WAREHOUSE} 人员，供头字段 {@code practitioner_id} 与制单候选人选择。
     */
    private List<OptionDto> warehouseKeeperOptions;

    /**
     * 单据状态下拉：{@code value} 为 {@link com.zxplat.domain.share.enums.SupplyStatus#getCode()} 持久化码。
     */
    private List<OptionDto> statusOptions;

    /**
     * 物品来源表下拉：药品/耗材 {@code productTableName} 白名单，与选品 {@link com.zxplat.application.inventory.warehouse.purchaseorder.request.PageProductsForPurchaseRequest#getProductTableName()} 一致。
     */
    private List<OptionDto> purchaseItemTableTypeOptions;
}
