package com.openhis.whale.booking.domain.waitlist.repository;

import com.openhis.whale.booking.domain.waitlist.entity.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {

    Optional<WaitlistEntry> findByPatientIdAndSlotIdAndStatusAndIsDeletedFalse(
        Long patientId, Long slotId, String status);

    Optional<WaitlistEntry> findFirstBySlotIdAndStatusAndIsDeletedFalseOrderByQueuePositionAsc(
        Long slotId, String status);

    List<WaitlistEntry> findBySlotIdAndStatusAndIsDeletedFalseOrderByQueuePositionAsc(
        Long slotId, String status);

    long countBySlotIdAndStatusAndIsDeletedFalse(Long slotId, String status);

    @Query("""
        SELECT COALESCE(MAX(w.queuePosition), 0) FROM WaitlistEntry w
        WHERE w.slotId = :slotId AND w.status = :status AND w.isDeleted = false
        """)
    Integer findMaxQueuePositionBySlotIdAndStatus(
        @Param("slotId") Long slotId,
        @Param("status") String status);

    @Query("""
        SELECT w.slotId, COUNT(w) FROM WaitlistEntry w
        WHERE w.slotId IN :slotIds AND w.status = :status AND w.isDeleted = false
        GROUP BY w.slotId
        """)
    List<Object[]> countWaitingGroupBySlotIds(
        @Param("slotIds") Collection<Long> slotIds,
        @Param("status") String status);
}
