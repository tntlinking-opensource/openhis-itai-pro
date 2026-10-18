/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.waitlist.service;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.plugin.booking.domain.share.WaitlistStatusConstants;
import com.zxplat.plugin.booking.domain.waitlist.entity.WaitlistEntry;
import com.zxplat.plugin.booking.domain.waitlist.repository.WaitlistEntryRepository;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 候补队列写侧（BR-EXT-011）。
 *
 * <p>不变量：同一患者同一号源仅允许一条等候中记录；顺位按号源内递增。</p>
 */
@Service
@RequiredArgsConstructor
public class WaitlistDomainService {

    private final WaitlistEntryRepository waitlistEntryRepository;

    /**
     * 患者加入号源候补队列。
     *
     * @param organizationId 机构主键
     * @param patientId 患者主键
     * @param slotId 号源主键
     * @param scheduleId 日程主键
     * @param operatorId 操作人
     * @return 新建的候补记录
     * @throws BusinessException 参数不完整或已在该号源候补中时
     */
    public WaitlistEntry join(
        Long organizationId,
        Long patientId,
        Long slotId,
        Long scheduleId,
        Long operatorId) {
        // 前置校验：候补入队须携带完整业务主键
        if (organizationId == null || patientId == null || slotId == null || scheduleId == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "候补参数不完整");
        }
        Optional<WaitlistEntry> existing = waitlistEntryRepository.findByPatientIdAndSlotIdAndStatusAndIsDeletedFalse(
            patientId, slotId, WaitlistStatusConstants.WAITING);
        // 同一患者同一号源不得重复入候补
        if (existing.isPresent()) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT, "已在该号源候补中");
        }
        // 加载/组装：分配该号源下一顺位
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
        // 持久化：落库候补记录
        return waitlistEntryRepository.save(entry);
    }

    /**
     * 取消等候中的候补记录。
     *
     * @param waitlistEntryId 候补主键
     * @param reason 取消原因
     * @param operatorId 操作人
     * @throws BusinessException 记录不存在或非等候中状态时
     */
    public void cancel(Long waitlistEntryId, String reason, Long operatorId) {
        WaitlistEntry entry = waitlistEntryRepository.findById(waitlistEntryId)
            .filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()))
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "候补记录不存在"));
        // 仅等候中候补可主动取消，已递补或已取消须走其他流程
        if (!WaitlistStatusConstants.WAITING.equals(entry.getStatus())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "仅等候中候补可取消");
        }
        entry.setStatus(WaitlistStatusConstants.CANCELLED);
        entry.setCancelReason(reason);
        entry.setUpdatedAt(LocalDateTime.now());
        entry.setUpdatedBy(operatorId);
        // 持久化：写入取消状态
        waitlistEntryRepository.save(entry);
    }

    /**
     * 取号源下顺位最靠前的等候中候补（递补候选）。
     *
     * @param slotId 号源主键
     * @return 下一候补；无则 empty
     */
    public Optional<WaitlistEntry> findNextWaiting(Long slotId) {
        return waitlistEntryRepository.findFirstBySlotIdAndStatusAndIsDeletedFalseOrderByQueuePositionAsc(
            slotId, WaitlistStatusConstants.WAITING);
    }

    /**
     * 标记候补已递补为正式预约。
     *
     * @param entry 候补实体
     * @param appointmentId 递补产生的预约主键
     * @param operatorId 操作人
     */
    public void markPromoted(WaitlistEntry entry, Long appointmentId, Long operatorId) {
        entry.setStatus(WaitlistStatusConstants.PROMOTED);
        entry.setPromotedAppointmentId(appointmentId);
        entry.setUpdatedAt(LocalDateTime.now());
        entry.setUpdatedBy(operatorId);
        // 持久化：候补转正式预约后收口状态
        waitlistEntryRepository.save(entry);
    }

    /**
     * 条件更新：仅 WAITING 状态可标记为 PROMOTED（GAP-BKG-005 并发提升）。
     *
     * @param waitlistEntryId 候补主键
     * @param appointmentId 正式预约主键
     * @param operatorId 操作人
     * @return 是否成功标记
     */
    public boolean markPromotedIfWaiting(Long waitlistEntryId, Long appointmentId, Long operatorId) {
        Optional<WaitlistEntry> row = waitlistEntryRepository.findById(waitlistEntryId)
            .filter(entry -> !Boolean.TRUE.equals(entry.getIsDeleted()));
        if (row.isEmpty()) {
            return false;
        }
        WaitlistEntry entry = row.get();
        if (!WaitlistStatusConstants.WAITING.equals(entry.getStatus())) {
            return false;
        }
        entry.setStatus(WaitlistStatusConstants.PROMOTED);
        entry.setPromotedAppointmentId(appointmentId);
        entry.setUpdatedAt(LocalDateTime.now());
        entry.setUpdatedBy(operatorId);
        try {
            waitlistEntryRepository.save(entry);
            return true;
        } catch (OptimisticLockingFailureException ex) {
            return false;
        }
    }

    /**
     * 统计号源下等候中候补人数。
     *
     * @param slotId 号源主键
     * @return 等候人数
     */
    public long countWaitingBySlotId(Long slotId) {
        return waitlistEntryRepository.countBySlotIdAndStatusAndIsDeletedFalse(slotId, WaitlistStatusConstants.WAITING);
    }

    /**
     * 批量统计多号源等候中候补人数。
     *
     * @param slotIds 号源主键列表
     * @return 号源 id → 等候人数
     */
    public Map<Long, Long> countWaitingBySlotIds(List<Long> slotIds) {
        Map<Long, Long> counts = new HashMap<>();
        // 空入参时直接返回空映射，避免无意义查询
        if (slotIds == null || slotIds.isEmpty()) {
            return counts;
        }
        List<WaitlistEntry> rows = waitlistEntryRepository.findBySlotIdInAndStatusAndIsDeletedFalse(
            slotIds, WaitlistStatusConstants.WAITING);
        // 遍历：按号源聚合等候人数供列表展示
        for (WaitlistEntry row : rows) {
            Long slotId = row.getSlotId();
            counts.merge(slotId, 1L, Long::sum);
        }
        return counts;
    }

    /**
     * 列出号源下全部等候中候补（按顺位升序）。
     *
     * @param slotId 号源主键
     * @return 候补列表
     */
    public List<WaitlistEntry> listWaitingBySlotId(Long slotId) {
        return waitlistEntryRepository.findBySlotIdAndStatusAndIsDeletedFalseOrderByQueuePositionAsc(
            slotId, WaitlistStatusConstants.WAITING);
    }

    private int nextQueuePosition(Long slotId) {
        Optional<WaitlistEntry> top = waitlistEntryRepository
            .findTopBySlotIdAndStatusAndIsDeletedFalseOrderByQueuePositionDesc(
                slotId, WaitlistStatusConstants.WAITING);
        // 该号源尚无候补时从 1 起编
        if (top.isEmpty() || top.get().getQueuePosition() == null) {
            return 1;
        }
        return top.get().getQueuePosition() + 1;
    }
}
