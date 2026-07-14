/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.document.inventoryitemsnapshot.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存明细快照读侧行模型（DM-DOC-001 SDD §4.6.2）。
 *
 * <p>备份 DTO 对外 {@code id} 语义映射 {@link #inventoryItemId}，非快照行 {@link #id}。</p>
 */
@Getter
@Setter
public class DocInventoryItemSnapshotRowQueryModel {

    /** 快照行主键（内部/审计）。 */
    private Long id;

    /** 源库存项主键（备份列表对外 id 语义）。 */
    private Long inventoryItemId;

    /** 批次单号。 */
    private String busNo;

    /** 快照时刻。 */
    private LocalDateTime snapshotTime;

    /** 物品定义表名。 */
    private String itemTable;

    /** 物品定义主键。 */
    private Long itemId;

    /** 收费项定义主键。 */
    private Long chargeItemDefinitionId;

    /** 库房主键。 */
    private Long warehouseId;

    /** 供应商标识。 */
    private Long supplierId;

    /** 项目编码。 */
    private String productBusNo;

    /** 项目名称。 */
    private String name;

    /** 商品名称。 */
    private String merchandiseName;

    /** 规格。 */
    private String totalVolume;

    /** 厂家/产地。 */
    private String manufacturer;

    /** 批准文号。 */
    private String approvalNumber;

    /** 医保码。 */
    private String ybNo;

    /** 追溯码。 */
    private String traceNo;

    /** 拼音。 */
    private String pyStr;

    /** 五笔。 */
    private String wbStr;

    /** 生产批号。 */
    private String lotNumber;

    /** 账面数量。 */
    private BigDecimal quantity;

    /** 拆零↔包装倍数。 */
    private BigDecimal packageUnitRatio;

    /** 包装单位 code。 */
    private String packageUnitCode;

    /** 批号进价（→ DTO {@code lotCostPrice}）。 */
    private BigDecimal lotNumberCostAmount;

    /** 批号售价（→ DTO {@code lotSalePrice}）。 */
    private BigDecimal lotNumberPriceAmount;

    /** 进价行总额。 */
    private BigDecimal lineCostTotal;

    /** 售价行总额。 */
    private BigDecimal linePriceTotal;

    /** 生产日期。 */
    private LocalDateTime productionDate;

    /** 效期。 */
    private LocalDateTime expirationDate;

    /** 剩余过期天数。 */
    private Integer remainingDays;

    /** 供应状态 code。 */
    private String inventoryStatusEnum;

    /** 医保等级。 */
    private Integer chrgitmLv;

    /** 药品分类 code。 */
    private String medicationCategoryCode;

    /** 耗材分类 code。 */
    private String deviceCategoryCode;

    /** 剂型 code。 */
    private String doseFormCode;
}
