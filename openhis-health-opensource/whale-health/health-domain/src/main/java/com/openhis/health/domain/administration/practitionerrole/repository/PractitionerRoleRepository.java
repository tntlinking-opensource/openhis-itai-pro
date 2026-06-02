/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.administration.practitionerrole.repository;

import com.openhis.health.domain.administration.practitionerrole.entity.PractitionerRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 医疗卫生人员角色（岗位）仓储。
 * <p>
 * 维护人员在机构、科室下的角色授权，支持唯一性校验与按人员/机构/激活状态查询。
 */
public interface PractitionerRoleRepository extends JpaRepository<PractitionerRole, Long>, JpaSpecificationExecutor<PractitionerRole> {
    /** 按主键查询人员角色。 */
    Optional<PractitionerRole> findById(Long id);

    /**
     * 判断指定人员、角色编码、机构、地点的组合是否已存在（用于新增前唯一性校验）。
     */
    boolean existsByPractitionerIdAndRoleCodeAndOrgIdAndLocationId(
        Long practitionerId, Integer roleCode, Long orgId, Long locationId);

    /**
     * 判断除指定主键外是否存在相同人员、角色、机构、地点组合（用于编辑时唯一性校验）。
     */
    boolean existsByPractitionerIdAndRoleCodeAndOrgIdAndLocationIdAndIdNot(
        Long practitionerId, Integer roleCode, Long orgId, Long locationId, Long id);

    /** 查询指定人员的全部角色记录。 */
    List<PractitionerRole> findAllByPractitionerId(Long practitionerId);

    /** 查询指定人员下仍处于激活状态的全部角色。 */
    List<PractitionerRole> findAllByPractitionerIdAndActiveFlagTrue(Long practitionerId);

    /** 按人员与角色编码查询角色列表。 */
    List<PractitionerRole> findAllByPractitionerIdAndRoleCode(Long practitionerId, Integer roleCode);

    /** 按人员与机构查询角色列表。 */
    List<PractitionerRole> findAllByPractitionerIdAndOrgId(Long practitionerId, Long orgId);

    /** 按人员与机构查询仍处于激活状态的角色列表。 */
    List<PractitionerRole> findAllByPractitionerIdAndOrgIdAndActiveFlagTrue(Long practitionerId, Long orgId);

    /** 按角色编码查询仍处于激活状态的岗位分配（用于岗位菜单下列表一次性解析人员 ID，避免 N 次按人查询）。 */
    List<PractitionerRole> findAllByRoleCodeAndActiveFlagTrue(Integer roleCode);
}
