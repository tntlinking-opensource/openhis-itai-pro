/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.returntowarehouse.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 科室退库单列表行（库房单据读模型展平，一行一明细）。
 */
@Getter
@Setter
public class ReturnToWarehouseListRowDto {

    /** 供应请求明细主键 {@code wkf_supply_request.id}。 */
    private Long id;
    /** 业务单号。 */
    private String busNo;
    /** 来源科室入库单号（{@code original_bus_no}）。 */
    private String originalBusNo;
    /** 单据类型 sortOrder（固定 14）。 */
    private Integer typeEnum;
    /** 单据状态 sortOrder。 */
    private Integer statusEnum;
    /** 供应类别 sortOrder。 */
    private Integer categoryEnum;
    /** 源科室药房库位主键。 */
    private Long sourceLocationId;
    /** 目的药库库位主键。 */
    private Long purposeLocationId;
    /** 供应商主键。 */
    private Long supplierId;
    /** 物品定义主键。 */
    private Long itemId;
    /** 物品来源表名。 */
    private String itemTable;
    /** 退库数量（拆零量）。 */
    private BigDecimal itemQuantity;
    /** 计量单位编码。 */
    private String unitCode;
    /** 批号。 */
    private String lotNumber;
    /** 追溯码（逗号分隔多码）。 */
    private String traceNo;
    /** 行单价（落库快照）。 */
    private BigDecimal price;
    /** 行合计金额（落库快照）。 */
    private BigDecimal totalPrice;
    /** 申请时间。 */
    private LocalDateTime applyTime;
    /** 审批时间。 */
    private LocalDateTime approvalTime;
}
