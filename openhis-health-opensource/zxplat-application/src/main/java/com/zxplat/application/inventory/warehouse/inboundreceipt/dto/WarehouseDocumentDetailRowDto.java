/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.inboundreceipt.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库房单据明细分页行 DTO（进货可关联订货、出库可关联请领等场景共用读模型装配）。
 *
 * <p>与 {@link com.zxplat.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel} 列语义对齐；
 * 字段名含 {@code Enum} 或业务类型/状态/类别语义且类型为 {@link String} 者，均为持久化 {@code getCode()}（B.10.8.1）。</p>
 */
@Getter
@Setter
public class WarehouseDocumentDetailRowDto {

    /** 读模型行主键。 */
    private Long id;
    /** 业务单号。 */
    private String busNo;
    /** 来源订货等业务单号。 */
    private String originalBusNo;
    /** 单据类型持久化码（{@link com.zxplat.domain.share.enums.SupplyType#getCode()}）。 */
    private String typeEnum;
    /** 单据状态持久化码（{@link com.zxplat.domain.share.enums.SupplyStatus#getCode()}）。 */
    private String statusEnum;
    /** 供应类别持久化码（{@link com.zxplat.domain.share.enums.SupplyCategory#getCode()}）。 */
    private String categoryEnum;
    /** 源位置主键。 */
    private Long sourceLocationId;
    /** 目标位置主键。 */
    private Long purposeLocationId;
    /** 供应商主键。 */
    private Long supplierId;

    /** 供应商展示名（应用层批量解析；未命中主数据时为 null）。 */
    private String supplierName;
    /** 制单人展示名（列表富化）。 */
    private String creatorName;
    /** 申请人/制单人主键（明细头行口径）。 */
    private Long applicantId;
    /** 目的库位展示名（列表富化）。 */
    private String purposeLocationName;
    /** 单据明细金额合计（列表聚合）。 */
    private BigDecimal totalAmount;
    /** 物品定义主键。 */
    private Long itemId;
    /** 物品来源表名。 */
    private String itemTable;
    /** 数量。 */
    private BigDecimal itemQuantity;
    /** 计量单位编码（字典持久化码，B.10.8.1）。 */
    private String unitCode;
    /** 批号。 */
    private String lotNumber;
    /** 追溯码。 */
    private String traceNo;
    /** 单价。 */
    private BigDecimal price;
    /** 行总价。 */
    private BigDecimal totalPrice;
    /** 申请时间。 */
    private LocalDateTime applyTime;
    /** 审批时间。 */
    private LocalDateTime approvalTime;
}
