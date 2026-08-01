/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.purchaseorder.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 订货预警快捷选品弹窗行（IF-WHS-PO-14）：品种维，无批号列。
 */
@Getter
@Setter
@Schema(description = "订货预警快捷选品行")
public class PurchaseWarningProductRowDto {

    /**
     * 产品定义主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "产品定义主键")
    private Long productDefinitionId;

    /**
     * 物品来源表名。
     */
    @Schema(description = "物品表名")
    private String productTableName;

    /**
     * 产品业务编码。
     */
    @Schema(description = "产品业务编码")
    private String productBusNo;

    /**
     * 产品名称。
     */
    @Schema(description = "产品名称")
    private String productName;

    /**
     * 规格展示（目录 {@code totalVolume}）。
     */
    @Schema(description = "规格")
    private String specification;

    /**
     * 是否命中最晚效期批近效。
     */
    @Schema(description = "是否命中效期")
    private Boolean hitExpire;

    /**
     * 是否命中低储。
     */
    @Schema(description = "是否命中低储")
    private Boolean hitLowStock;

    /**
     * 最晚效期批的效期日。
     */
    @Schema(description = "最晚效期日")
    private LocalDate latestExpirationDate;

    /**
     * 距今天数（可负，表示已过期）。
     */
    @Schema(description = "剩余天数")
    private Integer remainingDays;

    /**
     * 全院合计换算包装量（低储展示）。
     */
    @Schema(description = "全院包装合计")
    private BigDecimal hospitalWidePackageQty;
}
