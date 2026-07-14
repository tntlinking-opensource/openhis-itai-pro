/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicerequest.repository;

import com.openhis.health.domain.workflow.devicerequest.entity.DeviceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 耗材请求仓储。
 *
 * <p>约束：禁止 {@code @Query}；过滤口径（含软删）必须下推到数据库侧完成。</p>
 */
public interface DeviceRequestRepository extends JpaRepository<DeviceRequest, Long>,
    JpaSpecificationExecutor<DeviceRequest> {

    /**
     * 按主键查询。
     * @param id 条件
     * @return 查询结果
     */
    Optional<DeviceRequest> findById(Long id);

    /**
     * 按条件查询。
     * @param encounterId 条件
     * @return 查询结果
     */
    List<DeviceRequest> findByEncounterId(Long encounterId);

    /**
     * 按就诊与生成来源过滤耗材请求（门诊医生站医嘱列表 BR-ORD-010，与药品对称）。
     *
     * @param encounterId 就诊标识
     * @param generateSourceEnum 生成来源持久化码
     * @return 请求集合
     */
    List<DeviceRequest> findByEncounterIdAndGenerateSourceEnum(Long encounterId, String generateSourceEnum);

    /**
     * 按条件查询。
     * @param patientId 条件
     * @param encounterId 条件
     * @return 查询结果
     */
    List<DeviceRequest> findByPatientIdAndEncounterIdNot(Long patientId, Long encounterId);

    /**
     * 按条件查询。
     * @param sourceId 条件
     * @param zero 条件
     * @return 查询结果
     */
    List<DeviceRequest> findByRefundDeviceIdAndQuantityLessThan(Long sourceId, BigDecimal zero);

    /**
     * 按原耗材请求主键集合查询关联退耗/负向请求行（refund_device_id 命中）。
     *
     * @param refundDeviceIds 原耗材请求主键集合
     * @return 退耗关联请求集合
     */
    List<DeviceRequest> findByRefundDeviceIdIn(Collection<Long> refundDeviceIds);

    /**
     * 按来源锚点查询绑定耗材请求列表（取消执行 based_on 反查，DM-WKF-003 §6.2.10）。
     *
     * @param basedOnTable 来源表名
     * @param basedOnId    来源主键
     * @return 耗材请求列表
     */
    List<DeviceRequest> findByBasedOnTableAndBasedOnId(String basedOnTable, Long basedOnId);

    /**
     * 按来源表名与多条来源主键批量查询绑定耗材请求。
     *
     * @param basedOnTable 来源表名
     * @param basedOnIds   来源主键集合
     * @return 耗材请求列表
     */
    List<DeviceRequest> findByBasedOnTableAndBasedOnIdIn(String basedOnTable, Collection<Long> basedOnIds);

    /**
     * 判断指定器材定义是否已被有效耗材申请引用（目录编辑门控）。
     *
     * @param deviceDefId 器材定义主键 {@code device_def_id}
     * @return 存在未软删申请时为 true
     */
    boolean existsByDeviceDefIdAndIsDeletedFalse(Long deviceDefId);
}
