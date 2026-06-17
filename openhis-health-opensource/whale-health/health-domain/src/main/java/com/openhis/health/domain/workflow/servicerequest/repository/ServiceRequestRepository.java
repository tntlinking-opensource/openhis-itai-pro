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

package com.openhis.health.domain.workflow.servicerequest.repository;

import com.openhis.health.domain.workflow.servicerequest.entity.ServiceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

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
     * 按就诊主键集合与请求状态批量查询（护士站待校对计数等）。
     *
     * @param encounterIds 就诊主键集合
     * @param statusEnum   请求状态 code
     * @return 诊疗申请集合
     */
    List<ServiceRequest> findByEncounterIdInAndStatusEnum(Collection<Long> encounterIds, String statusEnum);

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
}
