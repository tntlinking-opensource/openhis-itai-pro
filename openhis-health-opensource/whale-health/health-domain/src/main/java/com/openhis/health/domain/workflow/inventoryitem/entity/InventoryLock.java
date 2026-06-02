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
import com.openhis.health.domain.share.enums.LockStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * 库存锁定记录（DM-WKF-008 §3.2）。
 *
 * <p>对应表：inventory_lock。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "inventory_lock")
public class InventoryLock extends BaseAuditedEntity {

    /** 关联库存项标识。 */
    @Column(name = "inventory_id", nullable = false)
    private Long inventoryId;

    /** 锁定标识：true 已锁定、false 未锁定。 */
    @Column(name = "lock_flag", nullable = false)
    private Boolean lockFlag;

    /** 锁定状态：见 LockStatus。 */
    @Column(name = "status", nullable = false)
    private String status;

    /** 业务单号。 */
    @Column(name = "bus_no", nullable = false)
    private String busNo;

    /** 同一业务单内的顺序号。 */
    @Column(name = "order_flag", nullable = false)
    private Integer orderFlag;

    /** 乐观锁版本号。 */
    @Version
    @Column(name = "version")
    private Integer version;

    /**
     * 锁定前置断言（PR-II-LOCK-001）。
     */
    public void assertLockable() {
        if (this.inventoryId == null) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "inventoryId 不能为空");
        }
        if (this.busNo == null || this.busNo.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "busNo 不能为空");
        }
        if (this.orderFlag == null || this.orderFlag < 0) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "orderFlag 不合法");
        }
    }

    /**
     * 锁定。
     */
    public void lockOn() {
        this.lockFlag = true;
        this.status = LockStatus.LOCKED.getCode();
    }

    /**
     * 按单号解锁前置断言（PR-II-LOCK-002）。
     */
    public void assertUnlockableByBusNo() {
        if (this.busNo == null || this.busNo.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "busNo 不能为空");
        }
    }

    /**
     * 正常解锁（LOCKED -> UNLOCKED）。
     */
    public void lockToUnlocked() {
        if (!Boolean.TRUE.equals(this.lockFlag)) {
            return;
        }
        this.lockFlag = false;
        this.status = LockStatus.UNLOCKED.getCode();
    }

    /**
     * 强制解锁前置断言（PR-II-LOCK-003）。
     */
    public void assertForceUnlockable() {
        if (this.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "lockId 不能为空");
        }
    }

    /**
     * 强制解锁（LOCKED -> FORCE_UNLOCKED）。
     */
    public void lockToForceUnlocked() {
        this.lockFlag = false;
        this.status = LockStatus.FORCE_UNLOCKED.getCode();
    }
}

