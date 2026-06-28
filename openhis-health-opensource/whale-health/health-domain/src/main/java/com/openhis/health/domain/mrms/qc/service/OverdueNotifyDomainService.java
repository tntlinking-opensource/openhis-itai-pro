/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.mrms.qc.service;

import com.openhis.health.domain.mrms.qc.entity.OverdueNotifyLog;
import com.openhis.health.domain.mrms.qc.repository.OverdueNotifyLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 逾期通知幂等记录写侧（{@code mrms_overdue_notify_log}）。
 *
 * <p>同一病案、同一通知类型、同一自然日仅发送一次；仅注入本聚合 {@link OverdueNotifyLogRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class OverdueNotifyDomainService {

    private final OverdueNotifyLogRepository overdueNotifyLogRepository;

    /**
     * 是否已在指定自然日发送过该类型通知。
     *
     * @param caseRecordId 病案主档主键
     * @param notifyTypeCode 通知类型 code（submit_overdue/archive_overdue 等）
     * @param notifyDate 自然日（按任务调度日）
     * @return true 表示已通知过，应跳过
     */
    public boolean alreadyNotified(Long caseRecordId, String notifyTypeCode, LocalDate notifyDate) {
        return overdueNotifyLogRepository.existsByCaseRecordIdAndNotifyTypeCodeAndNotifyDateAndIsDeletedFalse(
                caseRecordId, notifyTypeCode, notifyDate);
    }

    /**
     * 记录本次通知已发送，供后续幂等判断。
     *
     * @param caseRecordId 病案主档主键
     * @param tenantId 租户主键
     * @param notifyTypeCode 通知类型 code
     * @param notifyDate 自然日
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordNotified(Long caseRecordId, Long tenantId, String notifyTypeCode, LocalDate notifyDate) {
        // Cmd：写入幂等键（caseRecordId + type + notifyDate）
        OverdueNotifyLog row = new OverdueNotifyLog();
        row.setTenantId(tenantId);
        row.setCaseRecordId(caseRecordId);
        row.setNotifyTypeCode(notifyTypeCode);
        row.setNotifyDate(notifyDate);
        row.setCreatedAt(LocalDateTime.now());
        row.setIsDeleted(false);
        // 持久化
        overdueNotifyLogRepository.save(row);
    }
}
