/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.purchaserequest.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 科室采购申请明细行（新建/修订请求共用，IF-DEP-CPR-04/05）。
 *
 * <p>与领域 {@code wkf_supply_request} 行字段对齐；制单时刻 {@code occurrence_time} 由请求头
 * {@link com.zxplat.application.inventory.department.purchaserequest.request.CreateDeptPurchaseRequestRequest#getOccurrenceTime()}
 * 统一写入各行，明细 DTO 不含 {@code occurrenceTime}。</p>
 */
@Getter
@Setter
public class DeptPurchaseRequestLineDto {

    /** 物品来源物理表名，与供应请求 {@code item_table} 及选品白名单一致。 */
    private String itemTable;

    /** 物品定义主键，与 {@code item_id} 一致。 */
    private Long itemId;

    /** 申请数量（拆零口径正数）。 */
    private BigDecimal itemQuantity;

    /** 计量单位编码（字典持久化码）。 */
    private String unitCode;

    /** 协商/参考单价；落库 {@code price}。 */
    private BigDecimal price;

    /** 行总价；落库 {@code total_price}。 */
    private BigDecimal totalPrice;

    /** 行级备注；落库 {@code remake}；空白时由请求头 {@code remake} 回填。 */
    private String remake;
}
