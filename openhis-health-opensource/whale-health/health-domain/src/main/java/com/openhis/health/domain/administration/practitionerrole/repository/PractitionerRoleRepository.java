/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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

    /**
     * 按主键加载岗位分配（含已软删，供框架默认 findById 对齐）。
     *
     * @param id 岗位分配主键
     * @return 岗位分配实体
     */
    Optional<PractitionerRole> findById(Long id);

    /**
     * 按业务唯一键加载岗位分配（含已软删），供全量替换/再次分配时复用历史行。
     *
     * @param practitionerId 医务人员主键
     * @param roleId 平台角色主键
     * @param orgId 机构主键
     * @param locationId 地点主键（可空）
     * @return 同键全部历史行（可能含多条软删）
     */
    List<PractitionerRole> findAllByPractitionerIdAndRoleIdAndOrgIdAndLocationId(
        Long practitionerId, Long roleId, Long orgId, Long locationId);

    /**
     * 判断同一医务人员在相同角色、机构、地点组合下是否已存在有效岗位分配。
     *
     * @param practitionerId 医务人员主键
     * @param roleId 平台角色主键
     * @param orgId 机构主键
     * @param locationId 地点主键（可空）
     * @return {@code true} 表示已存在未软删记录
     */
    boolean existsByPractitionerIdAndRoleIdAndOrgIdAndLocationIdAndIsDeletedFalse(
        Long practitionerId, Long roleId, Long orgId, Long locationId);

    /**
     * 编辑场景：除当前记录外是否仍存在相同有效岗位组合（唯一性校验）。
     *
     * @param practitionerId 医务人员主键
     * @param roleId 平台角色主键
     * @param orgId 机构主键
     * @param locationId 地点主键（可空）
     * @param id 当前编辑记录主键
     * @return {@code true} 表示冲突
     */
    boolean existsByPractitionerIdAndRoleIdAndOrgIdAndLocationIdAndIdNotAndIsDeletedFalse(
        Long practitionerId, Long roleId, Long orgId, Long locationId, Long id);

    /**
     * 列出指定医务人员的全部岗位分配（含停用）。
     *
     * @param practitionerId 医务人员主键
     * @return 岗位分配列表
     */
    List<PractitionerRole> findAllByPractitionerId(Long practitionerId);

    /**
     * 列出指定医务人员的激活岗位分配。
     *
     * @param practitionerId 医务人员主键
     * @return 激活岗位列表
     */
    List<PractitionerRole> findAllByPractitionerIdAndActiveFlagTrue(Long practitionerId);

    /**
     * 按医务人员与平台角色列出岗位分配。
     *
     * @param practitionerId 医务人员主键
     * @param roleId 平台角色主键
     * @return 岗位分配列表
     */
    List<PractitionerRole> findAllByPractitionerIdAndRoleId(Long practitionerId, Long roleId);

    /**
     * 按医务人员与机构列出岗位分配。
     *
     * @param practitionerId 医务人员主键
     * @param orgId 机构主键
     * @return 岗位分配列表
     */
    List<PractitionerRole> findAllByPractitionerIdAndOrgId(Long practitionerId, Long orgId);

    /**
     * 按医务人员与机构列出激活岗位分配。
     *
     * @param practitionerId 医务人员主键
     * @param orgId 机构主键
     * @return 激活岗位列表
     */
    List<PractitionerRole> findAllByPractitionerIdAndOrgIdAndActiveFlagTrue(Long practitionerId, Long orgId);

    /**
     * 按平台角色列出全部激活岗位（全院维度，结果集可能较大）。
     *
     * @param roleId 平台角色主键
     * @return 激活岗位列表
     */
    List<PractitionerRole> findAllByRoleIdAndActiveFlagTrue(Long roleId);

    /**
     * 按平台角色与机构列出激活岗位。
     *
     * @param roleId 平台角色主键
     * @param orgId 机构主键
     * @return 激活岗位列表
     */
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

    /**
     * 统计指定医务人员在某平台角色下的激活岗位数（软删除外）。
     *
     * @param practitionerId 医务人员主键
     * @param roleId 平台角色主键
     * @return 激活岗位条数
     */
    long countByPractitionerIdAndRoleIdAndActiveFlagTrueAndIsDeletedFalse(Long practitionerId, Long roleId);
}
