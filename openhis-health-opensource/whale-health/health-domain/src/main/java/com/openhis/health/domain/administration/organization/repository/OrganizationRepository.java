/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.organization.repository;

import com.openhis.health.domain.administration.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 机构聚合仓储。
 */
public interface OrganizationRepository extends JpaRepository<Organization, Long>, JpaSpecificationExecutor<Organization> {

    /**
     * 按主键查询。
     * @param id 条件
     * @return 查询结果
     */
    Optional<Organization> findById(Long id);

    /**
     * 按业务号查询。
     * @param busNo 条件
     * @return 查询结果
     */
    Optional<Organization> findFirstByBusNo(String busNo);

    /**
     * 按业务号批量查询（登录 OU code=busNo 反查 HIS 组织，避免 N+1）。
     *
     * @param busNos 业务号集合；调用方保证非空
     * @return 命中的组织列表（同一 busNo 多条时由上层择一）
     */
    List<Organization> findByBusNoIn(Collection<String> busNos);

    /**
     * 判断层级编码是否已被占用（BR-ORG-001）。
     *
     * @param busNo 层级编码
     * @return 已存在为 true
     */
    boolean existsByBusNo(String busNo);

    /**
     * 判断层级编码是否被其他机构占用（修改/迁移场景排除自身）。
     *
     * @param busNo 层级编码
     * @param id    排除的机构主键
     * @return 其他机构已占用为 true
     */
    boolean existsByBusNoAndIdNot(String busNo, Long id);

    /**
     * 判断是否存在满足条件的记录。
     * @param parentId 条件
     * @param name 条件
     * @return 查询结果
     */
    boolean existsByParentIdAndName(Long parentId, String name);

    /**
     * 判断是否存在满足条件的记录。
     * @param parentId 条件
     * @param name 条件
     * @param id 条件
     * @return 查询结果
     */
    boolean existsByParentIdAndNameAndIdNot(Long parentId, String name, Long id);

    /**
     * 判断是否存在满足条件的记录。
     * @param parentId 条件
     * @param statusEnum 条件
     * @return 查询结果
     */
    boolean existsByParentIdAndStatusEnum(Long parentId, String statusEnum);

    /**
     * 按条件查询列表。
     * @param parentId 条件
     * @return 查询结果
     */
    List<Organization> findAllByParentId(Long parentId);

    /**
     * 按条件查询列表。
     * @param statusEnum 条件
     * @return 查询结果
     */
    List<Organization> findAllByStatusEnum(String statusEnum);

    /**
     * 按启用状态查询机构列表（通用树/字典：含 ACTIVE 平铺后组树）。
     *
     * @param statusEnum 状态 code（通常为 ACTIVE）
     * @return 按 displayOrder、id 升序的平铺列表
     */
    List<Organization> findAllByStatusEnumOrderByDisplayOrderAscIdAsc(String statusEnum);

    /**
     * 按条件查询列表。
     * @param statusEnum 条件
     * @param classEnum 条件
     * @return 查询结果
     */
    List<Organization> findAllByStatusEnumAndClassEnum(String statusEnum, com.openhis.health.domain.share.enums.OrganizationClassification classEnum);

    /**
     * 按状态与业务分类查询机构列表（门诊挂号/入院/护士站等场景下拉）。
     *
     * @param statusEnum 状态 code
     * @param classEnum  机构业务分类
     * @return 按 displayOrder、id 升序的平铺列表
     */
    List<Organization> findAllByStatusEnumAndClassEnumOrderByDisplayOrderAscIdAsc(
        String statusEnum,
        com.openhis.health.domain.share.enums.OrganizationClassification classEnum);

    /**
     * 按条件查询列表。
     * @param ids 条件
     * @return 查询结果
     */
    List<Organization> findAllByIdIn(Collection<Long> ids);

    /**
     * 按状态集合查询机构列表（科室管理页等：含 DRAFT/ACTIVE/INACTIVE，不含 DISCARDED）。
     *
     * @param statusEnums 状态 code 集合
     * @return 按 displayOrder、id 升序的平铺列表
     */
    List<Organization> findAllByStatusEnumInOrderByDisplayOrderAscIdAsc(Collection<String> statusEnums);

    /**
     * 按层级类型与状态集合查询机构列表（单租户医院唯一性等业务校验）。
     *
     * @param typeEnum     机构层级类型 code（如 {@code HOSPITAL}）
     * @param statusEnums  未废弃状态 code 集合
     * @return 匹配的机构平铺列表
     */
    List<Organization> findAllByTypeEnumAndStatusEnumIn(String typeEnum, Collection<String> statusEnums);

    /**
     * 按层级编码前缀查询机构（调整上级时递归重算后代 busNo）。
     *
     * @param busNoPrefix 被移动节点 busNo 加 {@code .} 后的前缀
     * @return 匹配的机构列表
     */
    List<Organization> findAllByBusNoStartingWith(String busNoPrefix);
}
