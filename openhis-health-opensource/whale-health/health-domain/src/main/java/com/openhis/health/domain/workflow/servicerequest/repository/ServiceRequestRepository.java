/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.servicerequest.repository;

import com.openhis.health.domain.workflow.servicerequest.entity.ServiceRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 服务申请仓储。
 *
 * <p>约束：禁止 {@code @Query}；过滤条件（含软删）必须下推数据库侧完成。</p>
 */
public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long>,
    JpaSpecificationExecutor<ServiceRequest> {

    /**
     * 按主键查询。
     * @param id 条件
     * @return 查询结果
     */
    Optional<ServiceRequest> findById(Long id);

    /**
     * 按条件查询。
     * @param encounterId 条件
     * @return 查询结果
     */
    List<ServiceRequest> findByEncounterId(Long encounterId);

    /**
     * 按条件查询。
     * @param patientId 条件
     * @param excludeEncounterId 条件
     * @return 查询结果
     */
    List<ServiceRequest> findByPatientIdAndEncounterIdNot(Long patientId, Long excludeEncounterId);

    /**
     * 按条件查询。
     * @param encounterId 条件
     * @param therapyEnum 条件
     * @return 查询结果
     */
    List<ServiceRequest> findByEncounterIdAndTherapyEnum(Long encounterId, String therapyEnum);

    /**
     * 按条件查询。
     * @param encounterId 条件
     * @param generateSourceEnum 条件
     * @return 查询结果
     */
    List<ServiceRequest> findByEncounterIdAndGenerateSourceEnum(Long encounterId, String generateSourceEnum);

    /**
     * 按条件查询。
     * @param activityId 条件
     * @return 查询结果
     */
    List<ServiceRequest> findByActivityId(Long activityId);

    /**
     * 判断指定诊疗定义是否已被有效服务申请引用（目录编辑门控）。
     *
     * @param activityId 诊疗定义主键 {@code activity_id}
     * @return 存在未软删申请时为 true
     */
    boolean existsByActivityIdAndIsDeletedFalse(Long activityId);

    /**
     * 统计满足条件的数量。
     * @param basedOnTable 条件
     * @param basedOnId 条件
     * @return 查询结果
     */
    Long countByBasedOnTableAndBasedOnId(String basedOnTable, Long basedOnId);

    /**
     * 按来源锚点查询派生服务申请列表（取消执行绑定项 based_on 反查，DM-WKF-004 §6.2.23）。
     *
     * @param basedOnTable 来源表名
     * @param basedOnId    来源主键
     * @return 服务申请列表
     */
    List<ServiceRequest> findByBasedOnTableAndBasedOnId(String basedOnTable, Long basedOnId);

    /**
     * 按配置规则锚点与生成来源查询未软删床位滚费长期医嘱（AS-IP-NUR-013 BR-IP-ARG-019）。
     *
     * @param basedOnTable       来源表名（{@code cus_charge_item_auto_gen}）
     * @param basedOnId          配置规则主键
     * @param generateSourceEnum 生成来源（{@code auto-roll-fees}）
     * @return 首条命中或 empty
     */
    Optional<ServiceRequest> findFirstByBasedOnTableAndBasedOnIdAndGenerateSourceEnumAndIsDeletedFalse(
        String basedOnTable,
        Long basedOnId,
        String generateSourceEnum);

    /**
     * 按来源表名与多条来源主键批量查询派生服务申请（取消执行批量 based_on 反查）。
     *
     * @param basedOnTable 来源表名
     * @param basedOnIds   来源主键集合
     * @return 服务申请列表
     */
    List<ServiceRequest> findByBasedOnTableAndBasedOnIdIn(String basedOnTable, Collection<Long> basedOnIds);

    /**
     * 按条件查询。
     * @param encounterId 条件
     * @param categoryEnums 条件
     * @return 查询结果
     */
    List<ServiceRequest> findByEncounterIdAndCategoryEnumIn(Long encounterId, List<String> categoryEnums);

    /**
     * 按条件查询。
     * @param refundServiceId 条件
     * @param zero 条件
     * @return 查询结果
     */
    List<ServiceRequest> findByRefundServiceIdAndQuantityLessThan(Long refundServiceId, java.math.BigDecimal zero);

    /**
     * 按原服务申请主键集合查询关联退诊疗/负向申请行（refund_service_id 命中）。
     *
     * @param refundServiceIds 原服务申请主键集合
     * @return 负向申请行列表
     */
    List<ServiceRequest> findByRefundServiceIdIn(Collection<Long> refundServiceIds);

    /**
     * 按就诊主键集合与请求状态批量查询（护士站待校对计数等）。
     *
     * @param encounterIds 就诊主键集合
     * @param statusEnum   请求状态 code
     * @return 诊疗申请集合
     */
    List<ServiceRequest> findByEncounterIdInAndStatusEnum(Collection<Long> encounterIds, String statusEnum);

    /**
     * 护士工作量：按就诊集合批量加载服务申请（DM-WKF-004 §6.2.26；AS-RPT-002 category enrich）。
     *
     * @param encounterIds 就诊主键集合
     * @return 服务申请列表
     */
    List<ServiceRequest> findByEncounterIdInAndIsDeletedFalse(Collection<Long> encounterIds);

    /**
     * 按就诊、申请单号与生成来源查询服务申请行（申请单编辑删旧、列表分组）。
     *
     * @param encounterId        就诊主键
     * @param prescriptionNo     申请单号（完整匹配）
     * @param generateSourceEnum 生成来源 code
     * @return 服务申请列表
     */
    List<ServiceRequest> findByEncounterIdAndPrescriptionNoAndGenerateSourceEnum(
        Long encounterId, String prescriptionNo, String generateSourceEnum);

    /**
     * 按主键加悲观写锁（门诊执行次数门控 GAP-EXE-006）。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ServiceRequest> findWithLockById(Long id);
}
