/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicedispense.repository;

import com.openhis.health.domain.workflow.devicedispense.entity.DeviceDispense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 耗材发放仓储。
 *
 * <p>约束：禁止 {@code @Query}；过滤条件必须下推数据库侧。</p>
 */
public interface DeviceDispenseRepository extends JpaRepository<DeviceDispense, Long>, JpaSpecificationExecutor<DeviceDispense> {

    /**
     * 按主键查询。
     * @param id 条件
     * @return 查询结果
     */
    Optional<DeviceDispense> findById(Long id);

    /**
     * 按条件查询。
     * @param idList 条件
     * @return 查询结果
     */
    List<DeviceDispense> findByIdIn(List<Long> idList);

    /**
     * 按条件查询。
     * @param deviceReqIdList 条件
     * @return 查询结果
     */
    List<DeviceDispense> findByDeviceReqIdIn(List<Long> deviceReqIdList);

    /**
     * 按条件查询。
     * @param procedureId 条件
     * @return 查询结果
     */
    List<DeviceDispense> findByProcedureId(Long procedureId);

    /**
     * 按条件查询。
     * @param encounterId 条件
     * @return 查询结果
     */
    List<DeviceDispense> findByEncounterId(Long encounterId);

    /**
     * 按就诊主键集合批量查询发放行（门诊护士站队列待发提醒；软删在读侧过滤；调用方须控制 IN 规模）。
     *
     * @param encounterIds 就诊主键集合
     * @return 发放行列表
     */
    List<DeviceDispense> findByEncounterIdIn(Collection<Long> encounterIds);

    /**
     * 按条件查询。
     * @param encounterId 条件
     * @param statusEnumList 条件
     * @return 查询结果
     */
    List<DeviceDispense> findByEncounterIdAndStatusEnumIn(Long encounterId, List<Integer> statusEnumList);

    /**
     * 按业务号查询。
     * @param busNo 条件
     * @return 查询结果
     */
    Optional<DeviceDispense> findByBusNo(String busNo);

    /**
     * 按条件查询。
     * @param supportInfo 条件
     * @return 查询结果
     */
    List<DeviceDispense> findBySupportInfo(String supportInfo);

    /**
     * 统计满足条件的数量。
     * @param deviceReqId 条件
     * @param statusEnum 条件
     * @return 查询结果
     */
    long countByDeviceReqIdAndStatusEnum(Long deviceReqId, String statusEnum);

    /**
     * 护士工作量：按就诊集合与发放状态批量查询（DM-WKF-007 §6.2.20；AS-RPT-002）。
     *
     * @param encounterIds  就诊主键集合
     * @param statusEnums   发放状态 code 集合
     * @return 发放行列表
     */
    List<DeviceDispense> findByEncounterIdInAndStatusEnumInAndIsDeletedFalse(
        Collection<Long> encounterIds,
        Collection<String> statusEnums);

    /**
     * 判断同耗材定义同批号是否存在未软删发放行（任意状态；BR-WHS-IN-018 同批号异价门禁）。
     *
     * @param deviceDefId 耗材定义主键
     * @param lotNumber   批号字面值
     * @return 存在则为 true
     */
    boolean existsByDeviceDefIdAndLotNumberAndIsDeletedFalse(Long deviceDefId, String lotNumber);
}
