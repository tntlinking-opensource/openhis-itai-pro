/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.purchaseorder.dto;



import lombok.Getter;

import lombok.Setter;



import java.math.BigDecimal;

import java.time.LocalDateTime;



/**

 *

 * <p>与领域 {@code wkf_supply_request} 行字段对齐；{@code unitCode} 等为字典或计量持久化码（B.10.8.1）。

 * 新建/修订请求共用（IF-WHS-PO-04/05）；{@link #occurrenceTime} 为业务制单时刻，由前端随明细传入并落库

 * {@code occurrence_time}（草稿阶段不写 {@code apply_time}）。</p>

 */

@Getter

@Setter

public class PurchaseOrderLineDto {



    /**

     * 物品来源物理表名，与供应请求 {@code item_table} 及选品 {@code productTableName} 白名单一致。

     */

    private String itemTable;



    /**

     * 物品定义主键，与 {@code item_id} 一致。

     */

    private Long itemId;



    /**

     * 订货数量（业务口径正数）。

     */

    private BigDecimal itemQuantity;



    /**

     * 计量单位编码（字典/持久化码），与行数量同一语义维度；须与 catalog 单位槽一致。

     */

    private String unitCode;



    /**

     * 含税或约定口径下的单价；落库 {@code price}，槽价锚点（BR-PRICE-SRC-001）。

     */

    private BigDecimal price;



    /**

     * 行总价；落库 {@code total_price}。

     */

    private BigDecimal totalPrice;



    /**

     * 制单或业务发生时刻；落库 {@code occurrence_time}，AppService 门控非空。

     */

    private LocalDateTime occurrenceTime;



    /**

     * 行级备注；落库 {@code remake}。

     */

    private String remake;

}
