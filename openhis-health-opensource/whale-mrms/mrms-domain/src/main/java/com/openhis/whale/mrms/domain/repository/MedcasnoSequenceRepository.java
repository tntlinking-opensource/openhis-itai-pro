package com.openhis.whale.mrms.domain.repository;

import com.openhis.whale.mrms.domain.entity.MedcasnoSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface MedcasnoSequenceRepository extends JpaRepository<MedcasnoSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MedcasnoSequence> findByTenantIdAndYearNo(Long tenantId, Integer yearNo);
}
