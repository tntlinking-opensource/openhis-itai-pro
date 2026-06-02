/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.workflow.inventoryitem.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存项聚合根（DM-WKF-008）。
 *
 * <p>对应表：wkf_inventory_item。</p>
 *
 * <p>边界：本聚合只维护库存数量与供应状态事实；单价/进价/售价等定价数据不在本表持久化，须查收费项定价（如 {@code adm_charge_item_definition}）。业务原因叙事由 SupplyDelivery、MedicationDispense、DeviceDispense 等上游聚合承载。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "wkf_inventory_item")
public class InventoryItem extends BaseAuditedEntity {

    /** 物品类别编码。 */
    @Column(name = "category_code")
    private String categoryCode;

    /** 物品定义表名。 */
    @Column(name = "item_table", nullable = false)
    private String itemTable;

    /** 物品定义标识。 */
    @Column(name = "item_id", nullable = false)
    private Long itemId;

    /** 物品名称（冗余）。 */
    @Column(name = "name", nullable = false)
    private String name;

    /** 拼音码。 */
    @Column(name = "py_str")
    private String pyStr;

    /** 五笔码。 */
    @Column(name = "wb_str")
    private String wbStr;

    /** 库存状态（ACTIVE/RETIRED），与 PublicationStatus 编码对齐。 */
    @Column(name = "inventory_status_enum", nullable = false)
    private String inventoryStatusEnum;

    /** 供应商标识。 */
    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    /** 描述文本。 */
    @Column(name = "description_text")
    private String descriptionText;

    /** 计量单位编码。 */
    @Column(name = "unit_code", nullable = false)
    private String unitCode;

    /** 当前库存数量。 */
    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    /** 特征扩展 JSON。 */
    @Column(name = "characteristic_json")
    private String characteristicJson;

    /** 产品批号。 */
    @Column(name = "lot_number", nullable = false)
    private String lotNumber;

    /** 生产日期。 */
    @Column(name = "production_date")
    private LocalDateTime productionDate;

    /** 效期。 */
    @Column(name = "expiration_date")
    private LocalDateTime expirationDate;

    /** 有效期月数。 */
    @Column(name = "validity_mon")
    private Integer validityMon;

    /** 货位标识。 */
    @Column(name = "location_store_id")
    private Long locationStoreId;

    /** 库房标识。 */
    @Column(name = "location_id", nullable = false)
    private Long locationId;

    /** 追溯码。 */
    @Column(name = "trace_no", length = 50000)
    private String traceNo;

    /** 追溯码包装层级。 */
    @Column(name = "packaging_levels")
    private Integer packagingLevels;

    /** 乐观锁版本号。 */
    @Version
    @Column(name = "version")
    private Integer version;

    /**
     * 新建前置断言（BR-II-006）。
     */
    public void assertCreatable() {
        if (this.itemTable == null || this.itemTable.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "itemTable 不能为空");
        }
        if (this.itemId == null) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "itemId 不能为空");
        }
        if (this.name == null || this.name.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "name 不能为空");
        }
        if (this.supplierId == null) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "supplierId 不能为空");
        }
        if (this.unitCode == null || this.unitCode.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "unitCode 不能为空");
        }
        if (this.quantity == null || this.quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "quantity 必须大于 0");
        }
        if (this.lotNumber == null || this.lotNumber.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "lotNumber 不能为空");
        }
        if (this.locationId == null) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "locationId 不能为空");
        }
    }

    /**
     * 初始化正常供应状态（ACTIVE）。
     */
    public void initActive() {
        this.inventoryStatusEnum = PublicationStatus.ACTIVE.getCode();
    }

    /**
     * 增加数量前置断言。
     */
    public void assertIncreaseAllowed() {
        this.assertNotDeleted();
        this.assertActiveSupply();
    }

    /**
     * 减少数量前置断言（BR-II-001）。
     */
    public void assertDecreaseAllowed(BigDecimal decreaseQty, Boolean allowNegative) {
        this.assertNotDeleted();
        this.assertActiveSupply();
        if (decreaseQty == null || decreaseQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "decreaseQty 必须大于 0");
        }
        if (allowNegative == null) {
            allowNegative = false;
        }
        if (!allowNegative) {
            if (this.quantity == null || this.quantity.compareTo(decreaseQty) < 0) {
                throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "库存不足，无法扣减");
            }
        }
    }

    /**
     * 增加数量。
     */
    public void increaseQuantity(BigDecimal increaseQty) {
        if (increaseQty == null || increaseQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "increaseQty 必须大于 0");
        }
        if (this.quantity == null) {
            this.quantity = BigDecimal.ZERO;
        }
        this.quantity = this.quantity.add(increaseQty);
    }

    /**
     * 减少数量。
     */
    public void decreaseQuantity(BigDecimal decreaseQty) {
        if (this.quantity == null) {
            this.quantity = BigDecimal.ZERO;
        }
        this.quantity = this.quantity.subtract(decreaseQty);
    }

    /**
     * 停供前置断言。
     */
    public void assertStopSupplyAllowed() {
        this.assertNotDeleted();
        if (PublicationStatus.RETIRED.getCode().equals(this.inventoryStatusEnum)) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "已停供库存不允许重复停供");
        }
    }

    /**
     * 停供（ACTIVE -> RETIRED）。
     */
    public void activeToRetired() {
        this.assertStopSupplyAllowed();
        this.inventoryStatusEnum = PublicationStatus.RETIRED.getCode();
    }

    /**
     * 恢复供应前置断言。
     */
    public void assertResumeSupplyAllowed() {
        this.assertNotDeleted();
        if (!PublicationStatus.RETIRED.getCode().equals(this.inventoryStatusEnum)) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "仅已停供库存允许恢复供应");
        }
    }

    /**
     * 恢复供应（RETIRED -> ACTIVE）。
     */
    public void retiredToActive() {
        this.assertResumeSupplyAllowed();
        this.inventoryStatusEnum = PublicationStatus.ACTIVE.getCode();
    }

    /**
     * 清零删除前置断言（BR-II-009）。
     */
    public void assertZeroAndNoPending(Boolean applicationConfirmedNoPendingDelivery) {
        this.assertNotDeleted();
        if (this.quantity == null || this.quantity.compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "仅库存为 0 时允许清零删除");
        }
        if (applicationConfirmedNoPendingDelivery == null || !applicationConfirmedNoPendingDelivery) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "需应用层确认无未完成关联业务");
        }
    }

    /**
     * 逻辑删除。
     */
    public void logicalDelete() {
        this.setIsDeleted(true);
    }

    /**
     * 私有方法：assertNotDeleted。
     */
    private void assertNotDeleted() {
        if (Boolean.TRUE.equals(this.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "库存项已删除");
        }
    }

    /**
     * 私有方法：assertActiveSupply。
     */
    private void assertActiveSupply() {
        if (!PublicationStatus.ACTIVE.getCode().equals(this.inventoryStatusEnum)) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "仅正常供应库存允许增减");
        }
    }
}

