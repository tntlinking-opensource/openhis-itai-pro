/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.outpatient.repository;

import com.openhis.chs.domain.outpatient.entity.YbPersonCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface YbPersonCacheRepository extends JpaRepository<YbPersonCache, Long> {

    Optional<YbPersonCache> findFirstByContractIdAndMdtrtCertTypeAndMdtrtCertNoAndTenantIdAndIsDeletedFalse(
        Long contractId, String mdtrtCertType, String mdtrtCertNo, Long tenantId);
}
