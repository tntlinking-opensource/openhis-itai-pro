/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.returntowarehouse.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药房退库单列表行（IF-PHA-RTW-02：按 {@code bus_no} 聚合，一行一单）。
 *
 * <p>整单合计写 {@code totalAmount}（明细行 {@code total_price} 之和）。
 * {@code supplierName}/{@code creatorName}/{@code approverName} 由应用层列表查询后批量富化。</p>
 */
@Getter
@Setter
public class ReturnToWarehouseListRowDto {

    /** 供应请求明细主键 {@code wkf_supply_request.id}。 */
    private Long id;
    /** 业务单号。 */
    private String busNo;
    /** 来源药房入库单号（{@code original_bus_no}）。 */
    private String originalBusNo;
    /** 单据类型 sortOrder（固定 14）。 */
    private Integer typeEnum;
    /** 单据状态 sortOrder。 */
    private Integer statusEnum;
    /** 供应类别 sortOrder。 */
    private Integer categoryEnum;
    /** 源药房库位主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceLocationId;
    /** 目的药库库位主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long purposeLocationId;
    /** 供应商主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long supplierId;
    /** 供应商展示名（列表富化）。 */
    private String supplierName;
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
    /** 单据总金额（聚合列表侧栏；一行一单）。 */
    private BigDecimal totalAmount;
    /** 申请人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long applicantId;
    /** 制单人展示名（列表富化）。 */
    private String creatorName;
    /** 审批人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long approverId;
    /** 审批人展示名（列表富化）。 */
    private String approverName;
    /** 申请时间。 */
    private LocalDateTime applyTime;
    /** 审批时间。 */
    private LocalDateTime approvalTime;
}
