/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.proceduredefinition.repository;

import com.zxplat.domain.clinical.proceduredefinition.entity.ProcedureDefinition;
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

    /**
     * 按挂靠 AD 查手术定义（开立/首页：activityId → PD 反查）。
     *
     * @param activityDefinitionId 诊疗项目主键
     * @return 命中列表（通常 0～1；多条时由读侧择优）
     */
    List<ProcedureDefinition> findByActivityDefinitionId(Long activityDefinitionId);

    /**
     * 按挂靠 AD 批量查手术定义。
     *
     * @param activityDefinitionIds 诊疗项目主键集合
     * @return 命中列表
     */
    List<ProcedureDefinition> findByActivityDefinitionIdIn(Collection<Long> activityDefinitionIds);
}
