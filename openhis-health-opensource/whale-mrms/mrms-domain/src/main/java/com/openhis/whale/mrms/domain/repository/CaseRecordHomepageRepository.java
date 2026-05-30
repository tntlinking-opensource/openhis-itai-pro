package com.openhis.whale.mrms.domain.repository;

import com.openhis.whale.mrms.domain.entity.CaseRecordHomepage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CaseRecordHomepageRepository extends JpaRepository<CaseRecordHomepage, Long> {

    Optional<CaseRecordHomepage> findByCaseRecordIdAndIsDeletedFalse(Long caseRecordId);

    Optional<CaseRecordHomepage> findByIdAndIsDeletedFalse(Long id);
}
