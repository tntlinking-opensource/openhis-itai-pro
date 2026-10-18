/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.purchaseorder.request;

import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 订货预警快捷选品分页入参（IF-WHS-PO-14）。
 *
 * <p>须已选定表头项目类型对应 {@link #productTableName}；筛选项见
 * {@link com.zxplat.domain.share.constants.InventoryWarningConstants}。
 * 可选 {@code keyword}（基类 {@link AbstractHealthPageRequest}）按名称/编码/拼音过滤弹窗行。</p>
 */
@Getter
@Setter
@Schema(description = "订货预警快捷选品分页 IF-WHS-PO-14")
public class PagePurchaseWarningProductsRequest extends AbstractHealthPageRequest {

    /**
     * 目的药库位置主键：与订货单头一致，门禁校验管辖药库。
     */
    @Schema(description = "目的药库位置主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long purposeLocationId;

    /**
     * 物品来源表名：须与表头项目类型一致（药品或耗材定义表）；决定预警收窄范围。
     */
    @Schema(description = "物品表名（药品/耗材）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productTableName;

    /**
     * 预警筛选项：{@code expire}（最晚效期批近效）/ {@code lowStock}（仅低于最低）/ {@code all}（并集）。
     */
    @Schema(description = "预警筛：expire|lowStock|all", requiredMode = Schema.RequiredMode.REQUIRED)
    private String warningFilter;
}
