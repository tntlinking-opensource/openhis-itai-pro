package com.openhis.whale.booking.domain.healthcheckup.repository;

import com.openhis.whale.booking.domain.healthcheckup.entity.GroupCheckupOrg;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface GroupCheckupOrgRepository extends JpaRepository<GroupCheckupOrg, Long>, JpaSpecificationExecutor<GroupCheckupOrg> {

    Optional<GroupCheckupOrg> findByOrgCodeAndIsDeletedFalse(String orgCode);
}
