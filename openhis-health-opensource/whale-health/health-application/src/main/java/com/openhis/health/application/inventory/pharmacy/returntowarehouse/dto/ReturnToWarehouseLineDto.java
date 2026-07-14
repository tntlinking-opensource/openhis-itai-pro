/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.returntowarehouse.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药房退库新建/修订请求体中的明细行。
 */
@Getter
@Setter
public class ReturnToWarehouseLineDto {

    /** 物品来源表名。 */
    private String itemTable;
    /** 物品定义主键。 */
    private Long itemId;
    /** 物品数量（拆零量）。 */
    private BigDecimal itemQuantity;
    /** 计量单位编码。 */
    private String unitCode;
    /** 批号。 */
    private String lotNumber;
    /** 追溯码（逗号分隔多码）。 */
    private String traceNo;
    /** 业务发生/制单时间。 */
    private LocalDateTime occurrenceTime;
    /** 原因说明。 */
    private String reason;
    /** 备注。 */
    private String remake;
}
