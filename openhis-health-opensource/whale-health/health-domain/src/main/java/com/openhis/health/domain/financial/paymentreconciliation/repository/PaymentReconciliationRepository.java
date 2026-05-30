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

package com.openhis.health.domain.financial.paymentreconciliation.repository;

import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentReconciliation;
import com.openhis.health.domain.share.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 支付流水仓储。
 */
public interface PaymentReconciliationRepository extends JpaRepository<PaymentReconciliation, Long> {
    /**
     * 按主键查询。
     * @param id 条件
     * @return 查询结果
     */
    Optional<PaymentReconciliation> findById(Long id);

    /**
     * 按条件查询。
     * @param encounterId 条件
     * @return 查询结果
     */
    List<PaymentReconciliation> findByEncounterId(Long encounterId);

    /**
     * 按条件查询。
     * @param ids 条件
     * @return 查询结果
     */
    List<PaymentReconciliation> findByIdIn(Collection<Long> ids);

    /**
     * 判断是否存在满足条件的记录。
     * @param relationId 条件
     * @return 查询结果
     */
    boolean existsByRelationId(Long relationId);

    /**
     * 按条件查询。
     * @param relationId 条件
     * @return 查询结果
     */
    List<PaymentReconciliation> findByRelationId(Long relationId);

    /**
     * 按条件查询。
     * @param from 条件
     * @param to 条件
     * @return 查询结果
     */
    List<PaymentReconciliation> findByBillDateBetween(LocalDateTime from, LocalDateTime to);

    /**
     * 按条件查询。
     * @param patientId 条件
     * @param statusEnum 条件
     * @return 查询结果
     */
    List<PaymentReconciliation> findByPatientIdAndStatusEnum(Long patientId, PaymentStatus statusEnum);

    /**
     * 按条件查询。
     * @param encounterId 条件
     * @param statusEnums 条件
     * @return 查询结果
     */
    List<PaymentReconciliation> findByEncounterIdAndStatusEnumIn(Long encounterId, Collection<PaymentStatus> statusEnums);

    /**
     * 按就诊批量查询指定资金态流水（如预结算草稿 DRAFT），用于挂号列表回填支付草稿主键。
     *
     * @param encounterIds 就诊主键集合
     * @param statusEnum   支付状态
     * @return 命中流水列表
     */
    List<PaymentReconciliation> findByEncounterIdInAndStatusEnum(Collection<Long> encounterIds, PaymentStatus statusEnum);
}

