/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.outpatient.repository;

import com.openhis.chs.domain.outpatient.entity.YbEncounterPlatformMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface YbEncounterPlatformMapRepository extends JpaRepository<YbEncounterPlatformMap, Long> {

    Optional<YbEncounterPlatformMap> findFirstByEncounterIdAndContractIdAndTenantIdAndIsDeletedFalse(
        Long encounterId, Long contractId, Long tenantId);

    Optional<YbEncounterPlatformMap> findFirstByEncounterIdAndContractIdAndMedTypeAndTenantIdAndIsDeletedFalse(
        Long encounterId, Long contractId, String medType, Long tenantId);

    Optional<YbEncounterPlatformMap> findFirstByEncounterIdAndContractIdAndMdtrtIdAndTenantIdAndIsDeletedFalse(
        Long encounterId, Long contractId, String mdtrtId, Long tenantId);

    List<YbEncounterPlatformMap> findByEncounterIdInAndTenantIdAndIsDeletedFalse(
        Collection<Long> encounterIds, Long tenantId);
}
