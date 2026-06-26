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

package com.openhis.whale.booking.domain.slot.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.SlotStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 号源时段聚合根（DM-WKF-010）。
 *
 * <p>对应表：wkf_slot。</p>
 *
 * <p>边界：本聚合仅维护时间片、free/busy 状态与容量/占用事实；预约合同主数据由 Appointment 聚合表达。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "wkf_slot")
public class Slot extends BaseAuditedEntity {

    /** 机构标识（冗余自 Schedule）。 */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /** 所属日程标识。 */
    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    /** 开始时刻。 */
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    /** 结束时刻。 */
    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    /** 状态码（FHIR Slot.status：free/busy/...）。 */
    @Column(name = "status_code", nullable = false)
    private String statusCode;

    /** 额定容量（默认 1）。 */
    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    /** 已预约计数。 */
    @Column(name = "booked_count", nullable = false)
    private Integer bookedCount;

    /** 超售标记。 */
    @Column(name = "overbooked_flag", nullable = false)
    private Boolean overbookedFlag;

    /** 服务大类快照 JSON（覆盖 Schedule 同字段语义）。 */
    @Column(name = "service_category_json")
    private String serviceCategoryJson;

    /** 服务类型快照 JSON（覆盖 Schedule 同字段语义）。 */
    @Column(name = "service_type_json")
    private String serviceTypeJson;

    /** 专科快照 JSON（覆盖 Schedule 同字段语义）。 */
    @Column(name = "specialty_json")
    private String specialtyJson;

    /** 预约类型/场景快照 JSON。 */
    @Column(name = "appointment_type_json")
    private String appointmentTypeJson;

    /** 备注。 */
    @Column(name = "comment_text")
    private String commentText;

    /** 乐观锁版本号（用于并发占用/释放）。 */
    @Version
    @Column(name = "version")
    private Long version;

    /**
     * 初始化为可约态（free，bookedCount=0，overbookedFlag=false）。
     *
     * @param organizationId 机构标识
     * @param scheduleId 日程标识
     * @param startTime 开始时刻
     * @param endTime 结束时刻
     * @param capacity 容量
     */
    public void initFree(Long organizationId, Long scheduleId, LocalDateTime startTime, LocalDateTime endTime, Integer capacity) {
        if (organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        if (scheduleId == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "scheduleId 不能为空");
        }
        this.organizationId = organizationId;
        this.scheduleId = scheduleId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.assertTimeWindowValid(startTime, endTime);
        this.capacity = this.normalizeCapacity(capacity);
        this.bookedCount = 0;
        this.overbookedFlag = false;
        this.statusCode = SlotStatus.FREE.getCode();
    }

    /**
     * 时间窗口合法性断言（BR-SLT-001）。
     */
    public void assertTimeWindowValid(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "startTime/endTime 不能为空");
        }
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "endTime 必须大于 startTime");
        }
    }

    /**
     * 封锁前断言（BR-SLT-006）。
     */
    public void assertBlockable() {
        this.assertNotEnteredInError();
    }

    /**
     * 迁移为封锁态（busy-unavailable）。
     *
     * @param reason 封锁原因（可选）
     */
    public void toBusyUnavailable(String reason) {
        this.assertBlockable();
        this.statusCode = SlotStatus.BUSY_UNAVAILABLE.getCode();
        if (reason != null && !reason.isBlank()) {
            this.commentText = reason;
        }
    }

    /**
     * 解封前断言。
     */
    public void assertUnblockable() {
        this.assertNotEnteredInError();
        if (!SlotStatus.BUSY_UNAVAILABLE.getCode().equals(this.statusCode)) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "仅 busy-unavailable 允许解封");
        }
    }

    /**
     * busy-unavailable -> free 或 busy（BR-SLT-008）。
     */
    public void busyUnavailableToFreeOrBusy() {
        this.assertUnblockable();
        this.refreshStatusByBookedCount();
    }

    /**
     * 记一笔预约占用前断言（BR-SLT-005/006）。
     *
     * @param allowOverbook 是否允许超售
     */
    public void assertBookable(Boolean allowOverbook) {
        this.assertNotEnteredInError();
        if (SlotStatus.BUSY_UNAVAILABLE.getCode().equals(this.statusCode)) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "封锁时段不可预约");
        }
        if (this.capacity == null || this.capacity <= 0) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "capacity 非法");
        }
        if (this.bookedCount == null || this.bookedCount < 0) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "bookedCount 非法");
        }
        if (Boolean.TRUE.equals(allowOverbook)) {
            return;
        }
        if (this.bookedCount >= this.capacity) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "号源已满");
        }
    }

    /**
     * bookedCount++ 并刷新状态/超售标记（BR-SLT-007/008）。
     *
     * @param allowOverbook 是否允许超售
     * @return 更新后的 bookedCount
     */
    public Integer increaseBookedCountAndRefreshStatus(Boolean allowOverbook) {
        this.assertBookable(allowOverbook);
        this.bookedCount = this.bookedCount + 1;
        this.refreshOverbookedFlag();
        this.refreshStatusByBookedCount();
        return this.bookedCount;
    }

    /**
     * 释放占用前断言。
     *
     * @param releaseCount 释放笔数
     */
    public void assertReleasable(Integer releaseCount) {
        this.assertNotEnteredInError();
        if (releaseCount == null || releaseCount <= 0) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "releaseCount 非法");
        }
        if (this.bookedCount == null || this.bookedCount < releaseCount) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "bookedCount 不足");
        }
    }

    /**
     * bookedCount -= releaseCount 并刷新状态/超售标记（BR-SLT-007/008）。
     *
     * @param releaseCount 释放笔数
     * @return 更新后的 bookedCount
     */
    public Integer decreaseBookedCountAndRefreshStatus(Integer releaseCount) {
        this.assertReleasable(releaseCount);
        this.bookedCount = this.bookedCount - releaseCount;
        this.refreshOverbookedFlag();
        if (SlotStatus.BUSY_UNAVAILABLE.getCode().equals(this.statusCode)) {
            return this.bookedCount;
        }
        this.refreshStatusByBookedCount();
        return this.bookedCount;
    }

    /**
     * 标暂定前断言。
     */
    public void assertTentativeMarkable() {
        this.assertNotEnteredInError();
        if (!SlotStatus.FREE.getCode().equals(this.statusCode)) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "仅 free 允许标暂定");
        }
        if (this.bookedCount == null || this.bookedCount != 0) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "bookedCount 必须为 0 才能标暂定");
        }
    }

    /**
     * free -> busy-tentative。
     */
    public void freeToBusyTentative() {
        this.assertTentativeMarkable();
        this.statusCode = SlotStatus.BUSY_TENTATIVE.getCode();
    }

    /**
     * 确认暂定前断言。
     */
    public void assertTentativeConfirmable() {
        this.assertNotEnteredInError();
        if (!SlotStatus.BUSY_TENTATIVE.getCode().equals(this.statusCode)) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "仅 busy-tentative 允许确认");
        }
    }

    /**
     * busy-tentative -> busy（并记一笔占用）。
     *
     * @param allowOverbook 是否允许超售
     * @return 更新后的 bookedCount
     */
    public Integer busyTentativeToBusy(Boolean allowOverbook) {
        this.assertTentativeConfirmable();
        this.statusCode = SlotStatus.BUSY.getCode();
        return this.increaseBookedCountAndRefreshStatus(allowOverbook);
    }

    /**
     * 标差错前断言。
     *
     * @param force 是否强制
     */
    public void assertEnteredInErrorMarkable(Boolean force) {
        if (SlotStatus.ENTERED_IN_ERROR.getCode().equals(this.statusCode)) {
            return;
        }
        if (Boolean.TRUE.equals(force)) {
            return;
        }
        if (this.bookedCount != null && this.bookedCount > 0) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "存在预约占用时不允许标差错");
        }
        if (SlotStatus.BUSY_TENTATIVE.getCode().equals(this.statusCode)) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "busy-tentative 不允许标差错");
        }
    }

    /**
     * 迁移为 entered-in-error 终态。
     *
     * @param reason 原因（可选）
     * @param force 是否强制
     */
    public void toEnteredInError(String reason, Boolean force) {
        this.assertEnteredInErrorMarkable(force);
        this.statusCode = SlotStatus.ENTERED_IN_ERROR.getCode();
        if (reason != null && !reason.isBlank()) {
            this.commentText = reason;
        }
    }

    /**
     * 更新覆盖服务快照字段。
     */
    public void updateOverrideServiceSnapshots(String serviceCategoryJson, String serviceTypeJson, String specialtyJson, String appointmentTypeJson) {
        this.assertNotEnteredInError();
        this.serviceCategoryJson = serviceCategoryJson;
        this.serviceTypeJson = serviceTypeJson;
        this.specialtyJson = specialtyJson;
        this.appointmentTypeJson = appointmentTypeJson;
    }

    /**
     * 更新备注。
     *
     * @param commentText 备注
     */
    public void updateComment(String commentText) {
        this.assertNotEnteredInError();
        if (commentText != null && commentText.length() > 512) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "commentText 超出长度限制");
        }
        this.commentText = commentText;
    }

    /**
     * 设置超售标记。
     *
     * @param overbookedFlag 超售标记
     */
    public void setOverbookedFlag(Boolean overbookedFlag) {
        this.overbookedFlag = Boolean.TRUE.equals(overbookedFlag);
    }

    /**
     * 删除前断言：无预约占用、非临时占用、未软删。
     */
    public void assertRetirable() {
        this.assertNotEnteredInError();
        if (Boolean.TRUE.equals(this.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "号源已删除");
        }
        if (this.bookedCount != null && this.bookedCount > 0) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "存在预约占用时不允许删除号源");
        }
        if (SlotStatus.BUSY_TENTATIVE.getCode().equals(this.statusCode)) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "临时占用中不允许删除号源");
        }
    }

    /**
     * 软删除号源（撤销误放号源；已约号源不可删）。
     */
    public void retire() {
        this.assertRetirable();
        this.setIsDeleted(true);
    }

    /**
     * entered-in-error 断言（BR-SLT-006）。
     */
    public void assertNotEnteredInError() {
        if (SlotStatus.ENTERED_IN_ERROR.getCode().equals(this.statusCode)) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "entered-in-error 不允许执行该操作");
        }
    }

    /**
     * 归一化容量，未提供时默认 1。
     *
     * @param capacity 容量
     * @return 归一化后的容量
     */
    private Integer normalizeCapacity(Integer capacity) {
        if (capacity == null) {
            return 1;
        }
        if (capacity <= 0) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "capacity 必须大于 0");
        }
        return capacity;
    }

    /**
     * 按 bookedCount 与 capacity 刷新超售标记（BR-SLT-007）。
     */
    private void refreshOverbookedFlag() {
        if (this.capacity == null || this.capacity <= 0) {
            this.overbookedFlag = false;
            return;
        }
        if (this.bookedCount != null && this.bookedCount > this.capacity) {
            this.overbookedFlag = true;
        } else {
            this.overbookedFlag = false;
        }
    }

    /**
     * 按 bookedCount 刷新 free/busy 语义（BR-SLT-008 默认口径 A）。
     */
    private void refreshStatusByBookedCount() {
        if (this.bookedCount != null && this.bookedCount > 0) {
            this.statusCode = SlotStatus.BUSY.getCode();
            return;
        }
        this.statusCode = SlotStatus.FREE.getCode();
    }
}

