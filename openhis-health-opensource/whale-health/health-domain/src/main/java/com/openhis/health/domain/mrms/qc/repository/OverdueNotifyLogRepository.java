/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.qc.repository;

import com.openhis.health.domain.mrms.qc.entity.OverdueNotifyLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

/**
 * 逾期通知幂等日志仓储。
 */
public interface OverdueNotifyLogRepository extends JpaRepository<OverdueNotifyLog, Long> {

    /**
     * 幂等判断：同病案、同类型、同自然日是否已通知。
     *
     * @param caseRecordId 病案主档主键
     * @param notifyTypeCode 通知类型 code
     * @param notifyDate 通知自然日
     * @return 已通知则 true
     */
    boolean existsByCaseRecordIdAndNotifyTypeCodeAndNotifyDateAndIsDeletedFalse(
            Long caseRecordId, String notifyTypeCode, LocalDate notifyDate);
}
