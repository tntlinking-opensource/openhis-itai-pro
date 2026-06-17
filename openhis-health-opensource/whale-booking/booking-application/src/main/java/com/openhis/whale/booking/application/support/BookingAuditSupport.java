package com.openhis.whale.booking.application.support;

import com.openhis.whale.core.infrastructure.auditing.AuditLogInfo;
import com.openhis.whale.core.infrastructure.auditing.IAuditingStore;
import com.openhis.whale.core.infrastructure.auditing.OperationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Booking 关键写操作审计（可选接入 whale-audit）。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BookingAuditSupport {

    @Autowired(required = false)
    private IAuditingStore auditingStore;

    private final BookingOperatorResolver bookingOperatorResolver;

    public void record(String entityType, Long entityId, String operationType, String detail) {
        if (auditingStore == null) {
            log.debug("booking audit skipped (no IAuditingStore): {} {} {}", entityType, operationType, entityId);
            return;
        }
        AuditLogInfo info = AuditLogInfo.builder()
            .entityType(entityType)
            .entityId(entityId)
            .operationType(this.resolveOperationType(operationType))
            .userId(bookingOperatorResolver.resolveOperatorId().orElse(null))
            .newState(detail)
            .createdAt(LocalDateTime.now())
            .build();
        auditingStore.save(info);
    }

    private OperationType resolveOperationType(String operationType) {
        if (operationType == null) {
            return OperationType.UPDATE;
        }
        try {
            return OperationType.valueOf(operationType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return OperationType.UPDATE;
        }
    }
}
