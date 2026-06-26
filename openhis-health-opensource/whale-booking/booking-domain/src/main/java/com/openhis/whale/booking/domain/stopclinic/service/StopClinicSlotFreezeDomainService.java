package com.openhis.whale.booking.domain.stopclinic.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.domain.stopclinic.entity.StopClinicSlotFreeze;
import com.openhis.whale.booking.domain.stopclinic.repository.StopClinicSlotFreezeRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 停诊预览源号源冻结（BR-EXT-003/004，TC-FRZ-01）。
 */
@Service
@RequiredArgsConstructor
public class StopClinicSlotFreezeDomainService {

    private final StopClinicSlotFreezeRepository stopClinicSlotFreezeRepository;

    /**
     * 预览后冻结/续期源号源。
     *
     * @return 最晚过期时间
     */
    public LocalDateTime applyPreviewFreeze(
        Long organizationId,
        Long scheduleId,
        List<Long> slotIds,
        int ttlMinutes,
        Long operatorId) {
        if (organizationId == null || scheduleId == null || slotIds == null || slotIds.isEmpty()) {
            return null;
        }
        int safeTtl = ttlMinutes;
        if (safeTtl <= 0) {
            safeTtl = 15;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(safeTtl);
        LocalDateTime latestExpiresAt = expiresAt;
        for (Long slotId : this.distinctIds(slotIds)) {
            StopClinicSlotFreeze freeze = stopClinicSlotFreezeRepository.findFirstBySlotIdAndIsDeletedFalse(slotId)
                .orElse(null);
            if (freeze != null && freeze.getExpiresAt().isAfter(now)) {
                freeze.setExpiresAt(expiresAt);
                freeze.setUpdatedAt(now);
                freeze.setUpdatedBy(operatorId);
                stopClinicSlotFreezeRepository.save(freeze);
            } else {
                if (freeze != null) {
                    this.softDelete(freeze, operatorId, now);
                }
                StopClinicSlotFreeze created = new StopClinicSlotFreeze();
                created.setOrganizationId(organizationId);
                created.setScheduleId(scheduleId);
                created.setSlotId(slotId);
                created.setExpiresAt(expiresAt);
                created.setCreatedAt(now);
                created.setCreatedBy(operatorId);
                created.setIsDeleted(Boolean.FALSE);
                stopClinicSlotFreezeRepository.save(created);
            }
        }
        return latestExpiresAt;
    }

    /**
     * 释放预览冻结（向导取消或执行完成后）。
     */
    public void releasePreviewFreeze(Long scheduleId, List<Long> slotIds, Long operatorId) {
        if (scheduleId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<StopClinicSlotFreeze> rows;
        if (slotIds == null || slotIds.isEmpty()) {
            rows = stopClinicSlotFreezeRepository.findByScheduleIdAndIsDeletedFalse(scheduleId);
        } else {
            rows = stopClinicSlotFreezeRepository.findByScheduleIdAndSlotIdInAndIsDeletedFalse(
                scheduleId, this.distinctIds(slotIds));
        }
        for (StopClinicSlotFreeze row : rows) {
            this.softDelete(row, operatorId, now);
        }
    }

    /**
     * 占号前断言：冻结中的号源不可新约。
     */
    public void assertSlotNotFrozenForNewBooking(Long slotId) {
        if (slotId == null) {
            return;
        }
        StopClinicSlotFreeze freeze = stopClinicSlotFreezeRepository.findFirstBySlotIdAndIsDeletedFalse(slotId)
            .orElse(null);
        if (freeze == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (!freeze.getExpiresAt().isAfter(now)) {
            this.softDelete(freeze, null, now);
            return;
        }
        throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "号源停诊预览冻结中，暂不可预约");
    }

    /**
     * 查询日程下仍有效的冻结号源集合。
     */
    public Set<Long> findActiveFrozenSlotIdsByScheduleId(Long scheduleId) {
        if (scheduleId == null) {
            return Set.of();
        }
        List<StopClinicSlotFreeze> rows = stopClinicSlotFreezeRepository.findByScheduleIdAndIsDeletedFalseAndExpiresAtAfter(
            scheduleId, LocalDateTime.now());
        Set<Long> slotIds = new HashSet<>();
        for (StopClinicSlotFreeze row : rows) {
            if (row.getSlotId() != null) {
                slotIds.add(row.getSlotId());
            }
        }
        return slotIds;
    }

    private List<Long> distinctIds(List<Long> slotIds) {
        List<Long> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Long slotId : slotIds) {
            if (slotId != null && seen.add(slotId)) {
                result.add(slotId);
            }
        }
        return result;
    }

    private void softDelete(StopClinicSlotFreeze freeze, Long operatorId, LocalDateTime now) {
        freeze.setIsDeleted(Boolean.TRUE);
        freeze.setDeletedAt(now);
        freeze.setDeletedBy(operatorId);
        freeze.setUpdatedAt(now);
        freeze.setUpdatedBy(operatorId);
        stopClinicSlotFreezeRepository.save(freeze);
    }
}
