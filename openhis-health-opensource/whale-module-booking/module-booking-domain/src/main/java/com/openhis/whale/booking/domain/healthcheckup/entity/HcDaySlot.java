/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.healthcheckup.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 体检日时段配额（bkg_hc_day_slot）。
 *
 * <p>分池键：机构 × 日期 × 时段 × 合同类型 ×（可选）校区。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_hc_day_slot")
public class HcDaySlot extends BaseAuditedEntity {

    /** 机构标识。 */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /** 所属日计划主键。 */
    @Column(name = "day_plan_id", nullable = false)
    private Long dayPlanId;

    /** 计划日期（冗余）。 */
    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    /** 时段开始时刻。 */
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    /** 时段结束时刻。 */
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /** 合同类型编码。 */
    @Column(name = "contract_type_code", nullable = false)
    private String contractTypeCode;

    /** 校区编码；无校区模块时为 null。 */
    @Column(name = "campus_code")
    private String campusCode;

    /** 额定容量。 */
    @Column(name = "capacity", nullable = false)
    private Integer capacity = 0;

    /** 已约人数。 */
    @Column(name = "booked_count", nullable = false)
    private Integer bookedCount = 0;

    /** 是否开放。 */
    @Column(name = "open_flag", nullable = false)
    private Boolean openFlag = Boolean.TRUE;

    /** 乐观锁版本号（占号/释号并发控制）。 */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    /**
     * 占号前断言：开放、容量大于 0、未满。
     */
    public void assertOccupyable() {
        if (Boolean.TRUE.equals(this.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "时段配额已删除");
        }
        if (!Boolean.TRUE.equals(this.openFlag)) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "时段档位未开放");
        }
        if (this.capacity == null || this.capacity <= 0) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "时段容量为 0 不可约");
        }
        if (this.bookedCount == null) {
            this.bookedCount = 0;
        }
        if (this.bookedCount >= this.capacity) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "时段配额已满");
        }
    }

    /**
     * 占号：已约人数加 1。
     */
    public void occupy() {
        this.assertOccupyable();
        this.bookedCount = this.bookedCount + 1;
    }

    /**
     * 释号前断言。
     */
    public void assertReleasable() {
        if (Boolean.TRUE.equals(this.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "时段配额已删除");
        }
        if (this.bookedCount == null || this.bookedCount <= 0) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "已约人数不足，无法释号");
        }
    }

    /**
     * 释号：已约人数减 1。
     */
    public void release() {
        this.assertReleasable();
        this.bookedCount = this.bookedCount - 1;
    }

    /**
     * 更新容量前断言：新容量不得小于已约人数。
     *
     * @param newCapacity 新容量
     */
    public void assertCapacityUpdatable(Integer newCapacity) {
        if (newCapacity == null || newCapacity < 0) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "容量非法");
        }
        int booked = 0;
        if (this.bookedCount != null) {
            booked = this.bookedCount;
        }
        if (newCapacity < booked) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT, "新容量不得小于已约人数");
        }
    }

    /**
     * 删除前断言：无已约占用。
     */
    public void assertDeletable() {
        if (this.bookedCount != null && this.bookedCount > 0) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT, "存在已约占用时不允许删除时段");
        }
    }

    /**
     * 计算剩余可约人数。
     *
     * @return 剩余人数
     */
    public int remainingCount() {
        int cap = 0;
        if (this.capacity != null) {
            cap = this.capacity;
        }
        int booked = 0;
        if (this.bookedCount != null) {
            booked = this.bookedCount;
        }
        int remaining = cap - booked;
        if (remaining < 0) {
            remaining = 0;
        }
        return remaining;
    }
}
