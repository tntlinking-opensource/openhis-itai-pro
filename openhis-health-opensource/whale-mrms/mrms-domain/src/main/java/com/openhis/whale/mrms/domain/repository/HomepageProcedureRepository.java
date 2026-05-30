package com.openhis.whale.mrms.domain.repository;

import com.openhis.whale.mrms.domain.entity.HomepageProcedure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HomepageProcedureRepository extends JpaRepository<HomepageProcedure, Long> {

    List<HomepageProcedure> findAllByCaseRecordIdAndIsDeletedFalseOrderBySortNoAsc(Long caseRecordId);
}
