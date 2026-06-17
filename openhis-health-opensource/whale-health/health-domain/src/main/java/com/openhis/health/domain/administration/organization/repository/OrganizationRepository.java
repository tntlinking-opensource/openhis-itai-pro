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
     * 按条件查询列表。
     * @param statusEnum 条件
     * @param classEnum 条件
     * @return 查询结果
     */
    List<Organization> findAllByStatusEnumAndClassEnum(String statusEnum, com.openhis.health.domain.share.enums.OrganizationClassification classEnum);

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

