package com.openhis.whale.booking.domain.waitlist.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.domain.share.WaitlistStatusConstants;
import com.openhis.whale.booking.domain.waitlist.entity.WaitlistEntry;
import com.openhis.whale.booking.domain.waitlist.repository.WaitlistEntryRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 候补队列写侧（BR-EXT-011）。
 */
@Service
@RequiredArgsConstructor
public class WaitlistDomainService {

    private final WaitlistEntryRepository waitlistEntryRepository;

    public WaitlistEntry join(
        Long organizationId,
        Long patientId,
        Long slotId,
        Long scheduleId,
        Long operatorId) {
        if (organizationId == null || patientId == null || slotId == null || scheduleId == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "候补参数不完整");
        }
        Optional<WaitlistEntry> existing = waitlistEntryRepository.findByPatientIdAndSlotIdAndStatusAndIsDeletedFalse(
            patientId, slotId, WaitlistStatusConstants.WAITING);
        if (existing.isPresent()) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT, "已在该号源候补中");
        }
        int nextPosition = this.nextQueuePosition(slotId);
        LocalDateTime now = LocalDateTime.now();
        WaitlistEntry entry = new WaitlistEntry();
        entry.setOrganizationId(organizationId);
        entry.setPatientId(patientId);
        entry.setSlotId(slotId);
        entry.setScheduleId(scheduleId);
        entry.setQueuePosition(nextPosition);
        entry.setStatus(WaitlistStatusConstants.WAITING);
        entry.setCreatedAt(now);
        entry.setCreatedBy(operatorId);
        entry.setUpdatedAt(now);
        entry.setUpdatedBy(operatorId);
        entry.setIsDeleted(Boolean.FALSE);
        return waitlistEntryRepository.save(entry);
    }

    public void cancel(Long waitlistEntryId, String reason, Long operatorId) {
        WaitlistEntry entry = waitlistEntryRepository.findById(waitlistEntryId)
            .filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()))
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "候补记录不存在"));
        if (!WaitlistStatusConstants.WAITING.equals(entry.getStatus())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "仅等候中候补可取消");
        }
        entry.setStatus(WaitlistStatusConstants.CANCELLED);
        entry.setCancelReason(reason);
        entry.setUpdatedAt(LocalDateTime.now());
        entry.setUpdatedBy(operatorId);
        waitlistEntryRepository.save(entry);
    }

    public Optional<WaitlistEntry> findNextWaiting(Long slotId) {
        return waitlistEntryRepository.findFirstBySlotIdAndStatusAndIsDeletedFalseOrderByQueuePositionAsc(
            slotId, WaitlistStatusConstants.WAITING);
    }

    public void markPromoted(WaitlistEntry entry, Long appointmentId, Long operatorId) {
        entry.setStatus(WaitlistStatusConstants.PROMOTED);
        entry.setPromotedAppointmentId(appointmentId);
        entry.setUpdatedAt(LocalDateTime.now());
        entry.setUpdatedBy(operatorId);
        waitlistEntryRepository.save(entry);
    }

    public long countWaitingBySlotId(Long slotId) {
        return waitlistEntryRepository.countBySlotIdAndStatusAndIsDeletedFalse(slotId, WaitlistStatusConstants.WAITING);
    }

    public Map<Long, Long> countWaitingBySlotIds(List<Long> slotIds) {
        Map<Long, Long> counts = new HashMap<>();
        if (slotIds == null || slotIds.isEmpty()) {
            return counts;
        }
        List<Object[]> rows = waitlistEntryRepository.countWaitingGroupBySlotIds(
            slotIds, WaitlistStatusConstants.WAITING);
        for (Object[] row : rows) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    public List<WaitlistEntry> listWaitingBySlotId(Long slotId) {
        return waitlistEntryRepository.findBySlotIdAndStatusAndIsDeletedFalseOrderByQueuePositionAsc(
            slotId, WaitlistStatusConstants.WAITING);
    }

    private int nextQueuePosition(Long slotId) {
        Integer max = waitlistEntryRepository.findMaxQueuePositionBySlotIdAndStatus(
            slotId, WaitlistStatusConstants.WAITING);
        return max == null ? 1 : max + 1;
    }
}
