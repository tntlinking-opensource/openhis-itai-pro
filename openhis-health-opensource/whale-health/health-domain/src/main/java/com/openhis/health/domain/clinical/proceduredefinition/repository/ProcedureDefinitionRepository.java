/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.proceduredefinition.repository;

import com.openhis.health.domain.clinical.proceduredefinition.entity.ProcedureDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 手术操作定义仓储。
 */
public interface ProcedureDefinitionRepository
    extends JpaRepository<ProcedureDefinition, Long>, JpaSpecificationExecutor<ProcedureDefinition> {

    boolean existsBySourceEnumAndProcedureCode(String sourceEnum, String procedureCode);

    boolean existsBySourceEnumAndProcedureCodeAndIdNot(String sourceEnum, String procedureCode, Long id);

    Optional<ProcedureDefinition> findBySourceEnumAndProcedureCode(String sourceEnum, String procedureCode);

    List<ProcedureDefinition> findBySourceEnumAndProcedureCodeIn(String sourceEnum, Collection<String> procedureCodes);

    List<ProcedureDefinition> findBySourceEnumAndClinicCode(String sourceEnum, String clinicCode);

    List<ProcedureDefinition> findBySourceEnumAndYbNo(String sourceEnum, String ybNo);

    List<ProcedureDefinition> findByClinicCodeIn(Collection<String> clinicCodes);

    List<ProcedureDefinition> findByProcedureCodeIn(Collection<String> procedureCodes);

    List<ProcedureDefinition> findByIdIn(List<Long> ids);
}
