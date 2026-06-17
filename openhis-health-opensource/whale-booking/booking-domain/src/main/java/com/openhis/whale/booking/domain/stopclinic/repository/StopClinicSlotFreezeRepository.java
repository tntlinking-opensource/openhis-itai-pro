package com.openhis.whale.booking.domain.stopclinic.repository;

import com.openhis.whale.booking.domain.stopclinic.entity.StopClinicSlotFreeze;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StopClinicSlotFreezeRepository extends JpaRepository<StopClinicSlotFreeze, Long> {

    Optional<StopClinicSlotFreeze> findFirstBySlotIdAndIsDeletedFalse(Long slotId);

    List<StopClinicSlotFreeze> findByScheduleIdAndSlotIdInAndIsDeletedFalse(Long scheduleId, Collection<Long> slotIds);

    List<StopClinicSlotFreeze> findByScheduleIdAndIsDeletedFalse(Long scheduleId);

    @Query("""
        SELECT f.slotId FROM StopClinicSlotFreeze f
        WHERE f.scheduleId = :scheduleId
          AND f.isDeleted = false
          AND f.expiresAt > :now
        """)
    List<Long> findActiveFrozenSlotIdsByScheduleId(
        @Param("scheduleId") Long scheduleId,
        @Param("now") LocalDateTime now);
}
