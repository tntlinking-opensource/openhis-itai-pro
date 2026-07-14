/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.share;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * 预约并发写路径异常映射（GAP-BKG-001/002/004/007）。
 */
@Component
public class BookingConcurrencySupport {

    static final String UK_OUTPATIENT_BOOKING_DEDUP = "uk_wkf_appt_patient_service_day";
    static final String UK_SLOT_ORG_TIME_WINDOW = "uk_wkf_slot_org_time_window";

    /**
     * 是否为门诊同日防重唯一索引冲突。
     */
    public boolean isOutpatientBookingDuplicate(Throwable throwable) {
        return this.isUniqueIndexConflict(throwable, UK_OUTPATIENT_BOOKING_DEDUP);
    }

    /**
     * 是否为虚拟物化 slot 时间窗唯一索引冲突。
     */
    public boolean isVirtualSlotMaterializeConflict(Throwable throwable) {
        return this.isUniqueIndexConflict(throwable, UK_SLOT_ORG_TIME_WINDOW);
    }

    /**
     * 门诊防重业务异常。
     */
    public BusinessException duplicateOutpatientBookingException() {
        return new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT, "同日同患者同服务重复预约");
    }

    /**
     * 号源乐观锁/并发占号冲突。
     */
    public BusinessException slotConflictException() {
        return new BusinessException(ErrorCodeConstants.SLOT_CONFLICT, "号源已被占用，请刷新后重试");
    }

    /**
     * 预约乐观锁冲突（改约/取消并发）。
     */
    public BusinessException appointmentConcurrentUpdateException() {
        return new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT, "预约已被修改，请刷新后重试");
    }

    /**
     * 将乐观锁异常映射为号源冲突业务码。
     */
    public BusinessException mapSlotOptimisticLock(OptimisticLockingFailureException ex) {
        return this.slotConflictException();
    }

    /**
     * 将乐观锁异常映射为预约并发更新业务码。
     */
    public BusinessException mapAppointmentOptimisticLock(OptimisticLockingFailureException ex) {
        return this.appointmentConcurrentUpdateException();
    }

    private boolean isUniqueIndexConflict(Throwable throwable, String indexName) {
        Throwable root = this.rootCause(throwable);
        if (!(root instanceof DataIntegrityViolationException)) {
            return false;
        }
        String message = root.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains(indexName);
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
