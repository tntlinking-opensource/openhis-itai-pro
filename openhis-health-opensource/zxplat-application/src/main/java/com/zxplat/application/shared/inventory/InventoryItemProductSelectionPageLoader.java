/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.inventory;

import com.zxplat.domain.workflow.inventoryitem.queries.InventoryItemPageQuery;
import com.zxplat.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.zxplat.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * 库存批号选品分页编排：关键字先按药品/耗材定义预解析（含商品名），再查库存项。
 *
 * <p>落点 {@code application.shared.inventory}（≥2 Feature：药库/药房/科室单据 + 护士站划价）。
 * 凡走 {@link InventoryItemQueryService#queryInventoryItemsByPage} 的选品入口应经本 Loader，
 * 避免库存冗余列无商品名导致「按商品名搜不到」。</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryItemProductSelectionPageLoader {

    private final InventoryCatalogFilter inventoryCatalogFilter;
    private final InventoryItemQueryService inventoryItemQueryService;

    /**
     * 预解析关键字定义主键后分页查询库存选品行。
     *
     * @param query    库存项分页条件（含 keyword / location / itemTable 等；原地写入 resolved*）
     * @param pageable Spring 分页；关键字非空时领域层会剥 Sort 以保留精确优先
     * @return 库存项读模型分页
     */
    public Page<InventoryItemModel> page(InventoryItemPageQuery query, Pageable pageable) {
        // Pre：商品名/编码等经定义 QS 预解析 item_id（B.6.4）
        this.inventoryCatalogFilter.enrichInventoryItemPageKeywordDefinitionIds(query);
        // Cmd：库存项本表分页（冗余列 OR 预解析 id）
        return this.inventoryItemQueryService.queryInventoryItemsByPage(query, pageable);
    }
}
