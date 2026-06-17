package com.openhis.whale.booking.domain.healthcheckup.repository;

import com.openhis.whale.booking.domain.healthcheckup.entity.GroupCheckupBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface GroupCheckupBatchRepository extends JpaRepository<GroupCheckupBatch, Long>, JpaSpecificationExecutor<GroupCheckupBatch> {

    List<GroupCheckupBatch> findByOrgIdAndIsDeletedFalseOrderByBookingDateDesc(Long orgId);
}
