package com.openhis.whale.mrms.domain.repository;

import com.openhis.whale.mrms.domain.entity.CaseRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface CaseRecordRepository extends JpaRepository<CaseRecord, Long>, JpaSpecificationExecutor<CaseRecord> {

    Optional<CaseRecord> findByIdAndIsDeletedFalse(Long id);

    Optional<CaseRecord> findByTenantIdAndEncounterIdAndIsDeletedFalse(Long tenantId, Long encounterId);

    boolean existsByTenantIdAndEncounterIdAndIsDeletedFalse(Long tenantId, Long encounterId);
}
