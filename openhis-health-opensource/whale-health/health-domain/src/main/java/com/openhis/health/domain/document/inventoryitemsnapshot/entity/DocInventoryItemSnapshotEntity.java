/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.document.inventoryitemsnapshot.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存明细快照聚合根（DM-DOC-001 · append-only 静表）。
 *
 * <p>对应表 {@code doc_inventory_item_snapshot}；业务语义不可 UPDATE（BR-DOC-SNAP-003）。</p>
 * <p>与 WKF {@code InventoryItem} 无聚合从属关系；仅保留写入时刻 {@code inventory_item_id} 引用。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "doc_inventory_item_snapshot")
public class DocInventoryItemSnapshotEntity extends BaseAuditedEntity {

    /** 批次单号（ABU + yyyyMMdd + 2 位序号，BR-DOC-SNAP-001）。 */
    @Column(name = "bus_no", nullable = false, length = 32)
    private String busNo;

    /** 本批次快照完成时刻（行内冗余）。 */
    @Column(name = "snapshot_time", nullable = false)
    private LocalDateTime snapshotTime;

    /** 写入时刻源 {@code wkf_inventory_item.id}（UK 成员，BR-DOC-SNAP-002）。 */
    @Column(name = "inventory_item_id", nullable = false)
    private Long inventoryItemId;

    /** 物品定义表名（如 {@code med_medication_definition}）。 */
    @Column(name = "item_table", nullable = false, length = 64)
    private String itemTable;

    /** 物品定义主键。 */
    @Column(name = "item_id", nullable = false)
    private Long itemId;

    /** 写入时收费项定义主键。 */
    @Column(name = "charge_item_definition_id")
    private Long chargeItemDefinitionId;

    /** 库房主键（不含货位，BR-DOC-SNAP-006）。 */
    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    /** 供应商标识。 */
    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    /** 项目编码。 */
    @Column(name = "product_bus_no", length = 64)
    private String productBusNo;

    /** 项目名称。 */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** 商品名称。 */
    @Column(name = "merchandise_name", length = 255)
    private String merchandiseName;

    /** 规格。 */
    @Column(name = "total_volume", length = 255)
    private String totalVolume;

    /** 厂家/产地。 */
    @Column(name = "manufacturer", length = 255)
    private String manufacturer;

    /** 批准文号。 */
    @Column(name = "approval_number", length = 128)
    private String approvalNumber;

    /** 医保码。 */
    @Column(name = "yb_no", length = 64)
    private String ybNo;

    /** 追溯码。 */
    @Column(name = "trace_no", length = 50000)
    private String traceNo;

    /** 拼音（备份关键字/排序）。 */
    @Column(name = "py_str", length = 255)
    private String pyStr;

    /** 五笔（备份关键字）。 */
    @Column(name = "wb_str", length = 255)
    private String wbStr;

    /** 生产批号。 */
    @Column(name = "lot_number", nullable = false, length = 255)
    private String lotNumber;

    /** 账面数量（拆零最小单位，冻结原值）。 */
    @Column(name = "quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal quantity;

    /** 拆零↔包装倍数（1 包装 = ratio 拆零）。 */
    @Column(name = "package_unit_ratio", precision = 18, scale = 4)
    private BigDecimal packageUnitRatio;

    /** 包装单位字典 code。 */
    @Column(name = "package_unit_code", length = 64)
    private String packageUnitCode;

    /** 批号进价（包装整包）。 */
    @Column(name = "lot_number_cost_amount", precision = 18, scale = 4)
    private BigDecimal lotNumberCostAmount;

    /** 批号售价（包装整包）。 */
    @Column(name = "lot_number_price_amount", precision = 18, scale = 4)
    private BigDecimal lotNumberPriceAmount;

    /** 进价行总额（写入时刻冻结，BR-DOC-SNAP-008）。 */
    @Column(name = "line_cost_total", precision = 18, scale = 4)
    private BigDecimal lineCostTotal;

    /** 售价行总额（写入时刻冻结，BR-DOC-SNAP-008）。 */
    @Column(name = "line_price_total", precision = 18, scale = 4)
    private BigDecimal linePriceTotal;

    /** 生产日期。 */
    @Column(name = "production_date")
    private LocalDateTime productionDate;

    /** 效期。 */
    @Column(name = "expiration_date")
    private LocalDateTime expirationDate;

    /** 剩余过期天数（写入时刻冻结）。 */
    @Column(name = "remaining_days")
    private Integer remainingDays;

    /** 供应状态（{@code PublicationStatus} code，BR-DOC-SNAP-005）。 */
    @Column(name = "inventory_status_enum", nullable = false, length = 32)
    private String inventoryStatusEnum;

    /** 医保等级码。 */
    @Column(name = "chrgitm_lv")
    private Integer chrgitmLv;

    /** 药品分类字典 code。 */
    @Column(name = "medication_category_code", length = 64)
    private String medicationCategoryCode;

    /** 耗材分类字典 code。 */
    @Column(name = "device_category_code", length = 64)
    private String deviceCategoryCode;

    /** 剂型字典 code。 */
    @Column(name = "dose_form_code", length = 64)
    private String doseFormCode;
}
