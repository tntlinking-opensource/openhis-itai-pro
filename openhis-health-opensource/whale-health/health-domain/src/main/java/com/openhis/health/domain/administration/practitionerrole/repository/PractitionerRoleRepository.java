/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.domain.administration.practitionerrole.repository;

import com.openhis.health.domain.administration.practitionerrole.entity.PractitionerRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 岗位分配（{@code adm_pr_assign}）仓储。
 */
public interface PractitionerRoleRepository extends JpaRepository<PractitionerRole, Long>, JpaSpecificationExecutor<PractitionerRole> {

    Optional<PractitionerRole> findById(Long id);

    boolean existsByPractitionerIdAndRoleIdAndOrgIdAndLocationId(
        Long practitionerId, Long roleId, Long orgId, Long locationId);

    boolean existsByPractitionerIdAndRoleIdAndOrgIdAndLocationIdAndIdNot(
        Long practitionerId, Long roleId, Long orgId, Long locationId, Long id);

    List<PractitionerRole> findAllByPractitionerId(Long practitionerId);

    List<PractitionerRole> findAllByPractitionerIdAndActiveFlagTrue(Long practitionerId);

    List<PractitionerRole> findAllByPractitionerIdAndRoleId(Long practitionerId, Long roleId);

    List<PractitionerRole> findAllByPractitionerIdAndOrgId(Long practitionerId, Long orgId);

    List<PractitionerRole> findAllByPractitionerIdAndOrgIdAndActiveFlagTrue(Long practitionerId, Long orgId);

    List<PractitionerRole> findAllByRoleIdAndActiveFlagTrue(Long roleId);

    List<PractitionerRole> findAllByRoleIdAndOrgIdAndActiveFlagTrue(Long roleId, Long orgId);

    /**
     * 按平台角色与病区位置收窄激活岗位（住院护士站责任护士下拉）。
     *
     * @param roleId     平台角色主键
     * @param locationId 病区位置主键
     * @return 岗位分配行
     */
    List<PractitionerRole> findAllByRoleIdAndLocationIdAndActiveFlagTrue(Long roleId, Long locationId);

    /**
     * 批量按机构收窄激活岗位（门诊挂号等场景：限定在门诊科室集合内有医师岗）。
     *
     * @param roleId 平台角色主键
     * @param orgIds 机构主键集合
     * @return 岗位分配行
     */
    List<PractitionerRole> findAllByRoleIdAndOrgIdInAndActiveFlagTrue(Long roleId, Collection<Long> orgIds);

    /**
     * 批量按药房/库位收窄激活岗位（发药调配药师下拉：{@code adm_pr_assign.location_id}）。
     *
     * @param roleId      平台角色主键
     * @param locationIds 药房等位置主键集合
     * @return 岗位分配行
     */
    List<PractitionerRole> findAllByRoleIdAndLocationIdInAndActiveFlagTrue(Long roleId, Collection<Long> locationIds);

    long countByPractitionerIdAndRoleIdAndActiveFlagTrueAndIsDeletedFalse(Long practitionerId, Long roleId);
}
