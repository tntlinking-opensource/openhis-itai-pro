/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.devicedefinition.repository;

import com.zxplat.domain.administration.devicedefinition.entity.DeviceDefinition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 器材定义聚合仓储（{@code adm_device_definition}）。
 *
 * <p>职责：业务编码唯一性校验、批量导入冲突判定与按状态分页加载。</p>
 */
public interface DeviceDefinitionRepository extends JpaRepository<DeviceDefinition, Long>, JpaSpecificationExecutor<DeviceDefinition> {

    /**
     * 按主键加载器材定义。
     *
     * @param id 主键
     * @return 实体；不存在时 empty
     */
    Optional<DeviceDefinition> findById(Long id);

    /**
     * 按主键集合批量加载器材定义。
     *
     * @param ids 主键集合
     * @return 实体列表
     */
    List<DeviceDefinition> findAllByIdIn(Collection<Long> ids);
    /**
     * 校验业务编码是否存在（BR-DEVDEF-001）。
     *
     * <p>DOC-CONFLICT：DM/SDD 未明确唯一性是否“包含已逻辑删除记录”；当前按“全局唯一”处理（含已逻辑删除记录）。</p>
     *
     * @param busNo 业务编码（已归一化 trim）
     * @return true=存在
     */
    boolean existsByBusNo(String busNo);
    /**
     * 批量按业务编码查询器材定义，用于导入冲突判定（DM-ADM-008 §6.1.5，SDD §5.1.5）。
     *
     * <p>说明：业务编码在聚合内被视为全局唯一键；批量导入必须一次性下推数据库侧查询，禁止循环单条查询。</p>
     *
     * @param busNos 业务编码集合（已归一化 trim）
     * @return 已存在的器材定义集合
     */
    List<DeviceDefinition> findByBusNoIn(Collection<String> busNos);

    /**
     * 按生命周期状态加载全部器材定义（主数据维护列表）。
     *
     * @param statusEnum 状态持久化码
     * @return 匹配的器材定义列表
     */
    List<DeviceDefinition> findAllByStatusEnum(String statusEnum);

    /**
     * 分页加载器材定义（无额外过滤，规模受 Pageable 约束）。
     *
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<DeviceDefinition> findAllBy(Pageable pageable);
}
