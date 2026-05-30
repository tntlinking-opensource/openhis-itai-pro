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

package com.openhis.health.domain.medication.medicationdispense.repository;

import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.share.enums.DispenseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 药品发放仓储。
 */
public interface MedicationDispenseRepository extends JpaRepository<MedicationDispense, Long>, JpaSpecificationExecutor<MedicationDispense> {

    /**
     * 按主键查询未删除发放记录。
     *
     * @param id 主键
     * @return 发放记录
     */
    Optional<MedicationDispense> findById(Long id);

    /**
     * 按就诊查询未删除发放记录。
     *
     * @param encounterId 就诊标识
     * @return 发放记录集合
     */
    List<MedicationDispense> findByEncounterId(Long encounterId);

    /**
     * 按就诊主键集合批量查询未删除发放记录（退药队列聚合等读场景，避免按就诊循环查库）。
     *
     * @param encounterIds 就诊主键集合
     * @return 发放记录集合
     */
    List<MedicationDispense> findByEncounterIdIn(Collection<Long> encounterIds);

    /**
     * 按汇总号查询未删除发放记录。
     *
     * @param summaryNo 汇总号
     * @return 发放记录集合
     */
    List<MedicationDispense> findBySummaryNo(String summaryNo);

    /**
     * 按医嘱集合查询未删除发放记录。
     *
     * @param medReqIds 医嘱标识集合
     * @return 发放记录集合
     */
    List<MedicationDispense> findByMedReqIdIn(Collection<Long> medReqIds);

    /**
     * 按主键集合查询未删除发放记录。
     *
     * @param ids 主键集合
     * @return 发放记录集合
     */
    List<MedicationDispense> findByIdIn(Collection<Long> ids);

    /**
     * 按医嘱和状态集合查询未删除发放记录。
     *
     * @param medReqId 医嘱标识
     * @param statuses 状态集合
     * @return 是否存在
     */
    boolean existsByMedReqIdAndStatusEnumIn(Long medReqId, Collection<DispenseStatus> statuses);

    /**
     * 按就诊与状态查询未删除发放记录。
     *
     * @param encounterId 就诊标识
     * @param statusEnum 状态编码
     * @return 发放记录集合
     */
    List<MedicationDispense> findByEncounterIdAndStatusEnum(Long encounterId, DispenseStatus statusEnum);

    /**
     * 判断药品是否存在有效发放引用。
     *
     * @param medicationId 药品标识
     * @return 是否存在引用
     */
    boolean existsByMedicationId(Long medicationId);
}

