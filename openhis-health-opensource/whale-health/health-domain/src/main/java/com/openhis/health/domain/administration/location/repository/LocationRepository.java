/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.location.repository;

import com.openhis.health.domain.administration.location.entity.Location;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 位置聚合仓储。
 */
public interface LocationRepository extends JpaRepository<Location, Long>, JpaSpecificationExecutor<Location> {

    /**
     * 查询未删除的位置。
     *
     * @param id 主键
     * @return 位置
     */
    Optional<Location> findById(Long id);

    /**
     * 按父级查询名称冲突。
     *
     * @param parentId 父级标识
     * @param name 名称
     * @return 是否存在
     */
    boolean existsByParentIdAndName(Long parentId, String name);

    /**
     * 按父级与状态集合查询子节点。
     *
     * @param parentId 父级标识
     * @param statusEnums 状态集合
     * @return 子节点集合
     */
    List<Location> findAllByParentIdAndStatusEnumIn(Long parentId, Collection<String> statusEnums);

    /**
     * 按机构与形态查询位置集合。
     *
     * @param organizationId 机构标识
     * @param formEnum 物理形式
     * @param statusEnum 状态
     * @return 位置列表
     */
    List<Location> findAllByOrganizationIdAndFormEnumAndStatusEnum(
        Long organizationId, String formEnum, String statusEnum);

    /**
     * 按物理形态与启用状态查询位置（不按 {@code organization_id} 收窄；进货页药房/药库全量主数据等）。
     */
    List<Location> findAllByFormEnumAndStatusEnum(String formEnum, String statusEnum);

    /**
     * 按机构与启用状态查询位置（不限物理形态，岗位分配下拉等）。
     */
    List<Location> findAllByOrganizationIdAndStatusEnum(Long organizationId, String statusEnum);

    /**
     * 按物理形态查询位置（不限状态，病区床位维护页等主数据场景）。
     *
     * @param formEnum 物理形式 code
     * @return 位置列表
     */
    List<Location> findAllByFormEnum(String formEnum);

    /**
     * 按机构与物理形态查询位置（不限状态）。
     *
     * @param organizationId 机构标识
     * @param formEnum         物理形式 code
     * @return 位置列表
     */
    List<Location> findAllByOrganizationIdAndFormEnum(Long organizationId, String formEnum);

    /**
     * 按父级查询未删除的位置。
     *
     * @param parentId 父级标识
     * @return 位置列表
     */
    List<Location> findAllByParentId(Long parentId);

    /**
     * 按父级与物理形式查询子位置。
     *
     * @param parentId 父级标识
     * @param formEnum 物理形式 code
     * @return 子位置列表
     */
    List<Location> findAllByParentIdAndFormEnum(Long parentId, String formEnum);

    /**
     * 按父级、物理形态与启用状态查询子位置（护士站病房树等）。
     *
     * @param parentId   父级标识
     * @param formEnum   物理形式 code
     * @param statusEnum 位置状态 code
     * @return 子位置列表
     */
    List<Location> findAllByParentIdAndFormEnumAndStatusEnum(
        Long parentId, String formEnum, String statusEnum);

    /**
     * 按主键集合、物理形态与启用状态批量查询（岗位分配收窄为管理病区）。
     *
     * @param ids        位置主键集合
     * @param formEnum   物理形式 code
     * @param statusEnum 位置状态 code
     * @return 位置列表
     */
    List<Location> findAllByIdInAndFormEnumAndStatusEnum(
        Collection<Long> ids, String formEnum, String statusEnum);

    /**
     * 按层级编码前缀查询位置（用于病区→病房→病床树展开）。
     *
     * @param busNoPrefix 父级 busNo 加 {@code .} 后的前缀
     * @return 匹配的位置列表
     */
    List<Location> findAllByBusNoStartingWith(String busNoPrefix);

    /**
     * 判断位置层级编码是否已被占用。
     *
     * @param busNo 层级编码
     * @return 已存在为 true
     */
    boolean existsByBusNo(String busNo);

    /**
     * 判断层级编码是否被其他位置占用（排除自身）。
     *
     * @param busNo 层级编码
     * @param id    排除的位置主键
     * @return 其他位置已占用为 true
     */
    boolean existsByBusNoAndIdNot(String busNo, Long id);

    /**
     * 按主键与床位形态加悲观写锁加载（分床/换床占床防 TOCTOU）。
     *
     * @param id       床位主键
     * @param formEnum 物理形式 code（须为 BED）
     * @return 未软删床位
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Location> findByIdAndFormEnumAndIsDeletedFalse(Long id, String formEnum);
}
