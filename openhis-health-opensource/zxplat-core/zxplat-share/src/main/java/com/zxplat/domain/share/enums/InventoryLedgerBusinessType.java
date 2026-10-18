/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 库存流水业务类型（读模型 {@code hisrm_inventory_ledger.business_type_enum}，持久化为 {@link #getCode()}）。
 *
 * <p>与 {@link SupplyType} 无关：本枚举描述的是「流水行语义」，不是供应请求单据类型。</p>
 */
public enum InventoryLedgerBusinessType implements EnumInterface {

    /** 入库/初始化（InventoryLedgerBusinessType，编码 {@code 1}）。 */
    INBOUND_OR_INIT(0L, "1", "入库/初始化", "库存项创建、数量增加等。", 1L),
    /** 出库（InventoryLedgerBusinessType，编码 {@code 2}）。 */
    OUTBOUND(0L, "2", "出库", "数量减少、分配扣减等。", 2L),
    /** 锁定（InventoryLedgerBusinessType，编码 {@code 3}）。 */
    LOCKED(0L, "3", "锁定", "库存锁定。", 3L),
    /** 解锁（InventoryLedgerBusinessType，编码 {@code 4}）。 */
    UNLOCKED(0L, "4", "解锁", "库存解锁。", 4L),
    /** 供应状态变更（InventoryLedgerBusinessType，编码 {@code 5}）。 */
    SUPPLY_STATUS_CHANGE(0L, "5", "供应状态变更", "停供、恢复供应。", 5L),
    /** 清零删除（InventoryLedgerBusinessType，编码 {@code 6}）。 */
    DELETED_ON_ZERO(0L, "6", "清零删除", "库存清零删除。", 6L),
    /** 低于预警阈值（InventoryLedgerBusinessType，编码 {@code 7}）。 */
    BELOW_THRESHOLD(0L, "7", "低于预警阈值", "库存低于预警阈值。", 7L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    InventoryLedgerBusinessType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    public Long getCodeSystemId() {
        return codeSystemId;
    }

    public String getCode() {
        return code;
    }

    public String getDisplay() {
        return display;
    }

    public String getDefinition() {
        return definition;
    }

    public Long getSortOrder() {
        return sortOrder;
    }
}
