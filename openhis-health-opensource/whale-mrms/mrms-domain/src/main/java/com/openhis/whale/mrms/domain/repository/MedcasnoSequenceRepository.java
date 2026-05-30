package com.openhis.whale.mrms.domain.repository;

import com.openhis.whale.mrms.domain.entity.MedcasnoSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MedcasnoSequenceRepository extends JpaRepository<MedcasnoSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM MedcasnoSequence s WHERE s.tenantId = :tenantId AND s.yearNo = :yearNo")
    Optional<MedcasnoSequence> findForUpdate(@Param("tenantId") Long tenantId, @Param("yearNo") Integer yearNo);
}
