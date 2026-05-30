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
}
