package com.openhis.whale.mrms.domain.repository;

import com.openhis.whale.mrms.domain.entity.HomepageDiagnosis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HomepageDiagnosisRepository extends JpaRepository<HomepageDiagnosis, Long> {

    List<HomepageDiagnosis> findAllByCaseRecordIdAndIsDeletedFalseOrderByDiagTypeCodeAscSortNoAsc(Long caseRecordId);

    List<HomepageDiagnosis> findAllByCaseRecordIdAndDiagTypeCodeAndIsDeletedFalseOrderBySortNoAsc(
            Long caseRecordId, String diagTypeCode);
}
